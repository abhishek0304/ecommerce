"""Set the Kubernetes overlay to images produced by this CI run."""
import json
import os
import re
from pathlib import Path
owner = os.environ["IMAGE_OWNER"].lower()
tag = os.environ["IMAGE_TAG"]
if not re.fullmatch(r"[a-z0-9][a-z0-9-]*", owner) or not re.fullmatch(r"[a-f0-9]{40}", tag):
    raise ValueError("Expected GitHub owner and full commit SHA")
path = Path("infra/k8s/observability/kustomization.yaml")
config = json.loads(path.read_text())
services = ["config-server", "service-registry", "api-gateway", "user-service", "product-service", "cart-service", "order-service", "notification-service"]
config["images"] = [{"name": "ecommerce/" + name, "newName": f"ghcr.io/{owner}/ecommerce-{name}", "newTag": tag} for name in services]
path.write_text(json.dumps(config, indent=2) + "\n")
