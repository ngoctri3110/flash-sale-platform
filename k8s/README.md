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

Teardown: `kubectl delete namespace flash-sale`.
