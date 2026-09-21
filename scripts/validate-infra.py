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
print("Infrastructure consistency checks passed.")
