#!/usr/bin/env bash
# One-time GCP setup for DocQA. Run from Cloud Shell or a machine with gcloud logged in:
#   PROJECT_ID=my-project GITHUB_REPO=your-user/docqa ./infra/setup-gcp.sh
# Safe to re-run: most steps tolerate "already exists". The index deploy takes ~30-60 min.
set -euo pipefail

: "${PROJECT_ID:?set PROJECT_ID}"
: "${GITHUB_REPO:?set GITHUB_REPO as owner/name}"
REGION="${REGION:-us-central1}"
DEPLOYED_INDEX_ID="${DEPLOYED_INDEX_ID:-docqa_deployed}"

gcloud config set project "$PROJECT_ID"
PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')

echo "==> Enabling APIs"
gcloud services enable run.googleapis.com artifactregistry.googleapis.com aiplatform.googleapis.com \
  sqladmin.googleapis.com secretmanager.googleapis.com iamcredentials.googleapis.com cloudbuild.googleapis.com

echo "==> Artifact Registry"
gcloud artifacts repositories create docqa --repository-format=docker --location="$REGION" 2>/dev/null || true

echo "==> Service accounts"
gcloud iam service-accounts create docqa-runtime --display-name="DocQA runtime" 2>/dev/null || true
gcloud iam service-accounts create docqa-deployer --display-name="DocQA GitHub deployer" 2>/dev/null || true
RUNTIME_SA="docqa-runtime@${PROJECT_ID}.iam.gserviceaccount.com"
DEPLOY_SA="docqa-deployer@${PROJECT_ID}.iam.gserviceaccount.com"
for role in roles/aiplatform.user roles/cloudsql.client roles/secretmanager.secretAccessor; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:$RUNTIME_SA" --role="$role" --condition=None >/dev/null
done
for role in roles/run.admin roles/artifactregistry.writer; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:$DEPLOY_SA" --role="$role" --condition=None >/dev/null
done
gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA" \
  --member="serviceAccount:$DEPLOY_SA" --role=roles/iam.serviceAccountUser >/dev/null

echo "==> Cloud SQL (Postgres)"
DB_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=')
gcloud sql instances create docqa-db --database-version=POSTGRES_16 --tier=db-f1-micro --region="$REGION" 2>/dev/null || true
gcloud sql databases create docqa --instance=docqa-db 2>/dev/null || true
gcloud sql users create docqa --instance=docqa-db --password="$DB_PASSWORD" 2>/dev/null \
  || gcloud sql users set-password docqa --instance=docqa-db --password="$DB_PASSWORD"
CLOUD_SQL_CONNECTION_NAME=$(gcloud sql instances describe docqa-db --format='value(connectionName)')

echo "==> Secrets"
printf '%s' "$DB_PASSWORD" | gcloud secrets create docqa-db-password --data-file=- 2>/dev/null \
  || printf '%s' "$DB_PASSWORD" | gcloud secrets versions add docqa-db-password --data-file=-
openssl rand -base64 48 | tr -d '\n' | gcloud secrets create docqa-jwt-secret --data-file=- 2>/dev/null || true

echo "==> Workload Identity Federation for GitHub Actions"
gcloud iam workload-identity-pools create github --location=global --display-name="GitHub" 2>/dev/null || true
gcloud iam workload-identity-pools providers create-oidc github-provider --location=global \
  --workload-identity-pool=github --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository=='${GITHUB_REPO}'" 2>/dev/null || true
gcloud iam service-accounts add-iam-policy-binding "$DEPLOY_SA" --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github/attribute.repository/${GITHUB_REPO}" >/dev/null
WIF_PROVIDER="projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github/providers/github-provider"

echo "==> Vertex AI Vector Search index (768 dims, streaming updates)"
cat > /tmp/index-metadata.json <<JSON
{"contentsDeltaUri":"","config":{"dimensions":768,"approximateNeighborsCount":20,
 "distanceMeasureType":"DOT_PRODUCT_DISTANCE",
 "algorithmConfig":{"treeAhConfig":{"leafNodeEmbeddingCount":500,"leafNodesToSearchPercent":7}}}}
JSON
gcloud ai indexes create --display-name=docqa-index --metadata-file=/tmp/index-metadata.json \
  --index-update-method=STREAM_UPDATE --region="$REGION"
INDEX_ID=$(gcloud ai indexes list --region="$REGION" --filter="displayName=docqa-index" --format='value(name.basename())' | head -1)

gcloud ai index-endpoints create --display-name=docqa-endpoint --public-endpoint-enabled --region="$REGION"
ENDPOINT_ID=$(gcloud ai index-endpoints list --region="$REGION" --filter="displayName=docqa-endpoint" --format='value(name.basename())' | head -1)

echo "==> Deploying index to endpoint (long-running, please wait)"
gcloud ai index-endpoints deploy-index "$ENDPOINT_ID" --deployed-index-id="$DEPLOYED_INDEX_ID" \
  --display-name=docqa-deployed --index="$INDEX_ID" --region="$REGION"
ENDPOINT_DOMAIN=$(gcloud ai index-endpoints describe "$ENDPOINT_ID" --region="$REGION" \
  --format='value(publicEndpointDomainName)')

cat <<OUT

=========== Add these in GitHub: Settings > Secrets and variables > Actions ===========
Secrets:
  GCP_WORKLOAD_IDENTITY_PROVIDER = $WIF_PROVIDER
  GCP_DEPLOY_SERVICE_ACCOUNT     = $DEPLOY_SA
Variables:
  GCP_PROJECT_ID                 = $PROJECT_ID
  GCP_REGION                     = $REGION
  CLOUD_SQL_CONNECTION_NAME      = $CLOUD_SQL_CONNECTION_NAME
  VERTEX_INDEX_ID                = $INDEX_ID
  VERTEX_INDEX_ENDPOINT_ID       = $ENDPOINT_ID
  VERTEX_DEPLOYED_INDEX_ID       = $DEPLOYED_INDEX_ID
  VERTEX_PUBLIC_ENDPOINT_DOMAIN  = $ENDPOINT_DOMAIN
Then push to main and the Deploy workflow does the rest.
=======================================================================================
OUT
