# Local Kubernetes

Use Docker Desktop Kubernetes or kind. Build images into the cluster (or load
them into kind), then apply the complete local stack.

```powershell
docker build -t flash-sale-backend:local backend
docker build -t flash-sale-frontend:local frontend
kubectl apply -k k8s
kubectl -n flash-sale rollout status deployment/backend
kubectl -n flash-sale rollout status deployment/frontend
kubectl -n flash-sale port-forward service/frontend 5173:80
```

The two replicas and rolling-update settings preserve availability; idempotency
keys make retried in-flight Order requests safe. CPU/memory requests reserve
predictable local capacity; limits prevent one JVM from starving Kafka/PostgreSQL.

Smoke test: submit Orders using unique idempotency keys, then verify available
quantity never drops below zero and inspect the `order-events.v1` consumer logs.

## Verified local run (2026-07-23)

On the Kind `flash-sale` cluster, PostgreSQL, Kafka, frontend, two backend HTTP
replicas, and the single `event-consumer` replica became Ready. A smoke Order
was accepted, Product browse returned five seeded products, Inventory changed
from 18 to 17, and the Kafka consumer created one `order_event_audit` record.
A backend rolling restart completed with `2/2` replicas Available. Run the
existing Concurrency Lab separately against the port-forwarded backend for a
machine-specific no-oversell load observation. The in-cluster 20-request run
accepted 17 Orders, rejected 3 with insufficient inventory, and left available
quantity at 0; no oversell occurred.

```powershell
kubectl -n flash-sale port-forward service/backend 18080:8080
.\scripts\smoke-kubernetes-concurrency.ps1
```

Or run the in-cluster Job after deleting its prior completed run:

```powershell
kubectl -n flash-sale delete job concurrency-smoke --ignore-not-found
kubectl apply -k k8s
kubectl -n flash-sale logs -f job/concurrency-smoke
```

Teardown: `kubectl delete namespace flash-sale`.
