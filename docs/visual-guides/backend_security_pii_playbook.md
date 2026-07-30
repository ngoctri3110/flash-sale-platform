# Playbook 16: Backend security, authorization và PII

## Phạm vi trung thực

MVP Flash Sale chưa có authentication thật. Đây là playbook design/capstone, không khẳng định repo hiện đã banking-grade.

## Threat model tối thiểu

| Asset | Threat | Control cần có |
|---|---|---|
| Order/customer data | IDOR, data leak | Authn + tenant/resource authorization |
| Money/booking action | Replay, privilege escalation | Idempotency, authorization, audit |
| Secret | Log/config leak | Secret manager, rotation, no secret in Git/log |
| PII | Over-collection/retention | Data minimization, encryption, retention/access policy |

## Bài thiết kế

Thêm roles CUSTOMER/ADMIN. Viết authorization matrix cho `GET products`, `POST orders`, `GET inventory`, `GET orders`. Quyết định identity source, JWT/session validation, service-to-service auth, audit fields. Viết test “customer A không đọc order B” trước code.

## Rule

- Authentication: bạn là ai; authorization: bạn được làm gì. Idempotency không thay hai thứ này.
- Validate input, parameterize SQL, allow-list sort; output encode/avoid sensitive errors.
- Least privilege DB/Kafka credentials; secrets qua environment/secret manager, không hard-code.
- Financial/compliance scope cần threat model, audit/reconciliation và review chuyên môn; không chỉ thêm Spring Security.

## Interview

“Tôi thiết kế authorization theo resource/tenant, test IDOR, không log secret/PII, và coi idempotency là dedup chứ không phải authentication.”
