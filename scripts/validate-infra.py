"""Offline consistency checks; does not contact Docker or a cluster."""
import json
import xml.etree.ElementTree as ET
from pathlib import Path
root = Path(__file__).resolve().parents[1]
compose = json.loads((root / "compose.yaml").read_text())
services = compose["services"]
names = ["config-server", "service-registry", "api-gateway", "user-service", "product-service", "cart-service", "order-service", "notification-service"]
for name in names:
    service = services[name]
    assert service["environment"]["SPRING_CLOUD_CONFIG_ENABLED"] == "false"
    module = service["build"]["args"]["MODULE"]
    ET.parse(root / module / "pom.xml")
    assert (root / module / "src/main/resources/application-container.properties").exists()
    assert all(not port.startswith("0.0.0.0:") for port in service.get("ports", []))
for name in ["user", "product", "cart", "order", "notification"]:
    db = services[name + "-db"]
    assert db["environment"]["MYSQL_DATABASE"] == name + "db"
    assert db["environment"]["MYSQL_USER"] == name
    assert "ports" not in db
    assert "$$MYSQL_PASSWORD" in db["healthcheck"]["test"][1]
for path in (root / "infra").rglob("*.json"):
    document = json.loads(path.read_text())
    if document.get("kind") == "List":
        for item in document["items"]:
            if item["kind"] == "StatefulSet":
                assert "volumeClaimTemplates" in item["spec"]
                assert "volumeClaimTemplates" not in item
assert ".env" in (root / ".dockerignore").read_text().splitlines()
provider_keys = {line.split("=", 1)[0] for line in (root / "notification-service/providers.env.example").read_text().splitlines() if line and not line.startswith("#")}
provider_deployments = []
for path in (root / "infra/k8s/base").glob("resources-*.json"):
    for item in json.loads(path.read_text())["items"]:
        if item["kind"] != "Deployment":
            continue
        env = item["spec"]["template"]["spec"]["containers"][0]["env"]
        refs = {entry["name"]: entry.get("valueFrom", {}).get("secretKeyRef", {}) for entry in env if entry["name"] in provider_keys}
        if refs:
            assert item["metadata"]["name"] == "notification-service", "Provider credentials must not reach unrelated services"
            assert set(refs) == provider_keys
            assert all(ref == {"name": "ecommerce-notification-providers", "key": key, "optional": True} for key, ref in refs.items())
            provider_deployments.append(item)
assert len(provider_deployments) == 1, "Notification provider wiring is missing or duplicated"
shipping_keys = {line.split("=", 1)[0] for line in (root / "order-service/shipping.env.example").read_text().splitlines() if line and not line.startswith("#")}
for key in shipping_keys:
    assert key in services["order-service"]["environment"]
shipping_deployments = []
for path in (root / "infra/k8s/base").glob("resources-*.json"):
    for item in json.loads(path.read_text())["items"]:
        if item["kind"] != "Deployment": continue
        env = item["spec"]["template"]["spec"]["containers"][0]["env"]
        refs = {e["name"]: e.get("valueFrom", {}).get("secretKeyRef", {}) for e in env if e["name"] in shipping_keys}
        if refs:
            assert item["metadata"]["name"] == "order-service"
            assert set(refs) == shipping_keys
            assert all(ref == {"name":"ecommerce-shipping-providers", "key":key, "optional":True} for key, ref in refs.items())
            shipping_deployments.append(item)
assert len(shipping_deployments) == 1
for module in ["user-service/user-service", "product-service", "cart-service", "order-service", "notification-service"]:
    main = root / module / "src/main/resources"
    for filename in ["application.properties", "application-container.properties"]:
        properties = (main / filename).read_text()
        assert "spring.jpa.hibernate.ddl-auto=validate" in properties
        assert "ddl-auto=update" not in properties
    assert "spring.flyway.baseline-on-migrate=false" in (main / "application.properties").read_text()
    assert "spring.flyway.clean-disabled=true" in (main / "application.properties").read_text()
    assert len(list((main / "db/migration").glob("V*.sql"))) >= 2
for service in ["api-gateway", "user-service", "product-service", "cart-service", "order-service", "notification-service"]:
    assert all(":9090:" not in port for port in services[service].get("ports", [])), "Management ports must stay private"
rules = json.loads((root / "infra/monitoring/alerts.yml").read_text())
assert len(rules["groups"][0]["rules"]) == 6
assert all("severity" in rule["labels"] and rule.get("for") for rule in rules["groups"][0]["rules"])
print("Infrastructure consistency checks passed.")
