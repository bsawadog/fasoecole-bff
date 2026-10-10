"""Validate workflow YAML and CloudFormation schemas without AWS mutations.

Install cfn-lint, then run this file from a checkout containing both repositories.
"""
from pathlib import Path
import subprocess
import yaml

root = Path(__file__).resolve().parents[3]
backend = root / "fasoecole-bff"
subprocess.run(
    ["cfn-lint", "-r", "eu-west-3", "-t",
     *map(str, sorted((backend / "infra/dev").glob("*.json")))],
    check=True,
)
for repository, workflow in [
    ("fasoecole-bff", "infra-dev"),
    ("fasoecole-bff", "deploy-dev"),
    ("fasoecole-spa", "deploy-dev"),
]:
    path = root / repository / ".github/workflows" / (workflow + ".yml")
    doc = yaml.safe_load(path.read_text(encoding="utf-8"))
    assert doc["permissions"]["id-token"] == "write", path
    assert doc["env"]["AWS_REGION"] == "eu-west-3", path
    expected = "${{ inputs.environment }}" if repository == "fasoecole-bff" and workflow == "deploy-dev" else "dev"
    assert all(job["environment"] == expected for job in doc["jobs"].values()), path
    print(f"{repository}/{workflow}: YAML valid")
