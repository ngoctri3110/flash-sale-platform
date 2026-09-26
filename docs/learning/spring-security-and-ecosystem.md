# Spring Security và cách chọn hệ sinh thái Spring

Tài liệu này dành cho người mới đã biết Java/Spring Boot cơ bản nhưng bị ngợp vì có quá nhiều starter và project Spring. Mục tiêu là hiểu đúng boundary, chọn đúng dependency cho một requirement cụ thể và giải thích được quyết định trong phỏng vấn.

Nếu bạn mới bắt đầu, hãy đọc [bài trực quan Spring Security cho người mới](../visual-guides/spring_security_beginner_guide.md) trước. Bài đó đi từ một request tạo Order, qua filter chain, đến kiểm tra ownership bằng sequence diagram và code ngắn; tài liệu này dùng để đào sâu và tra cứu.

## 1. Những gì repository đã có và còn thiếu

Repository đã có nền tảng về threat model, IDOR, PII, secret, authorization matrix, Spring proxy và self-invocation trong [`backend_security_pii_playbook.md`](../visual-guides/backend_security_pii_playbook.md) và [`spring_internals_security_playbook.md`](../visual-guides/spring_internals_security_playbook.md). MVP hiện **chưa có authentication thật**; `Customer` mới dùng để correlation và domain cố ý chưa có account.

Phần còn thiếu để gọi là hiểu Spring Security gồm: security filter chain, authentication provider, `SecurityContext`, request authorization, method authorization, session/JWT/OAuth2, CSRF/CORS, password hashing, test security và resource ownership. Không nên thêm `spring-boot-starter-security` vào MVP chỉ để có màn hình login; phải có identity source và authorization policy trước.

## 2. Security là nhiều lớp, không phải một dependency

| Lớp | Câu hỏi | Kiểm soát cần nghĩ đến |
|---|---|---|
| Identity/authentication | Ai đang gọi? | session, JWT, OIDC, API key, mTLS |
| Authorization | Identity đó được làm gì? | role, scope, permission, tenant, resource ownership |
| Transport | Kênh có bị nghe lén/sửa không? | HTTPS, certificate, HSTS, secure cookie |
| Browser/web | Trình duyệt có gửi request ngoài ý muốn không? | CSRF, CORS, SameSite, clickjacking headers |
| Input/output | Dữ liệu có làm hỏng hệ thống hoặc rò rỉ không? | validation, parameterization, output encoding, error policy |
| Application | Luồng nghiệp vụ có bị lạm dụng không? | rate limit, replay/idempotency, workflow/state checks |
| Data/secrets | Dữ liệu và credential có bị lộ không? | minimization, encryption, secret manager, rotation |
| Dependencies/supply chain | Code và package có đáng tin không? | pin version, SBOM, SCA, patching |
| Operations | Có phát hiện và điều tra được không? | audit, metrics, trace, alert, incident runbook |

Authentication không thay thế authorization. Idempotency key chỉ chống xử lý lặp; nó không chứng minh identity. Role `ADMIN` cũng không tự động cho phép đọc mọi resource nếu chính sách ownership/tenant yêu cầu kiểm tra thêm.

## 3. Luồng Spring Security cần hiểu

Trong ứng dụng Servlet, request đi qua servlet container rồi `DelegatingFilterProxy` nối vào Spring Security filter chain. Các filter có thể đọc credential, xác thực, đặt `Authentication` vào `SecurityContext`, rồi authorization filter quyết định request có được tiếp tục đến controller hay không. Đây là lý do security thường xảy ra trước controller và vì sao debug cần nhìn filter chain, principal, authorities và status `401/403`. [Spring Security Servlet architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)

Các khái niệm cốt lõi:

- **`SecurityFilterChain`**: cấu hình các rule cho HTTP request, login/resource server, CSRF, session và headers.
- **`Authentication`**: identity đã xác thực cùng authorities; không nên tự tin vào field từ request body.
- **`SecurityContext`**: nơi Spring giữ authentication cho request/thread hiện tại; phải cẩn thận khi chạy async hoặc đổi thread.
- **`AuthenticationManager`/`AuthenticationProvider`**: cơ chế xác thực credential theo loại identity.
- **`PasswordEncoder`**: hash password một chiều; không lưu plaintext và không tự hash bằng SHA-256.
- **`AuthorizationFilter`**: áp dụng rule cho request; `@PreAuthorize` là một lớp kiểm tra ở method boundary.
- **`AccessDeniedHandler` và `AuthenticationEntryPoint`**: phân biệt lỗi chưa xác thực với lỗi đã xác thực nhưng không đủ quyền.

`401` thường có nghĩa là chưa có authentication hợp lệ; `403` thường có nghĩa là đã xác thực nhưng bị từ chối hoặc vi phạm protection policy. API nên thống nhất error body và không làm lộ dữ liệu nhạy cảm.

## 4. Chọn mô hình identity

### Session/cookie

Dùng khi server-rendered web hoặc hệ thống cần session tập trung. Browser tự gửi cookie nên phải giữ CSRF protection, `HttpOnly`, `Secure`, `SameSite` phù hợp, session fixation protection và logout/revocation.

### JWT resource server

Dùng khi service nhận access token từ một identity provider. Resource server xác minh chữ ký, issuer, audience, expiry và scopes/claims; nó không nên tự tin vào một JWT chỉ vì decode được payload. JWT khó revoke ngay lập tức, vì vậy expiry, key rotation, introspection hoặc deny-list phải phù hợp với risk.

### OAuth2/OIDC login

Dùng khi ứng dụng login qua Google, Okta, Keycloak, Auth0 hoặc IdP doanh nghiệp. OIDC là lớp identity trên OAuth2; không tự triển khai authorization server nếu chỉ cần login. Validate `state`, `nonce`, redirect URI và mapping claim-to-authority.

### API key/service-to-service/mTLS

Dùng cho integration hoặc machine identity có phạm vi hẹp. Key phải có owner, scope, rotation, audit và rate limit. Với service quan trọng, cân nhắc workload identity hoặc mTLS thay vì chia sẻ một key tĩnh.

### Quyết định cho flash-sale

MVP hiện chưa có account nên chưa chọn một mô hình chính thức. Khi thêm auth, quyết định nên ghi thành ADR:

```text
Browser shop + admin UI → OIDC login hoặc session tùy frontend boundary.
Backend API → OAuth2 resource server kiểm tra bearer access token.
Customer ownership → subject/customerId mapping + resource authorization.
Admin actions → role/scope + explicit audit.
Internal worker/Kafka relay → service identity, không dùng Customer token.
```

## 5. CSRF, CORS và security headers

**CSRF** là việc browser tự gửi credential (thường là cookie) khiến một trang khác kích hoạt request ngoài ý muốn. Spring Security bật CSRF protection mặc định cho unsafe HTTP methods trong servlet application. Nếu API dùng bearer token trong `Authorization` header và không dùng cookie authentication, threat model có thể khác; không được tắt CSRF theo thói quen mà phải ghi rõ lý do. [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)

**CORS** là policy trình duyệt cho phép origin nào đọc response. CORS không phải authorization và không bảo vệ server khỏi curl/Postman. Cấu hình allow-list origin, method và header cần thiết; không dùng `*` cùng credentials.

**Headers** nên có HSTS khi chạy HTTPS, chống clickjacking, MIME sniffing và content-type confusion. CSP là lớp quan trọng cho web UI nhưng phải triển khai theo resource thực tế, tránh copy một header rồi nghĩ đã xong.

## 6. Authorization thực tế: từ URL đến resource

Request-level rule chưa đủ để chống IDOR:

```java
@PreAuthorize("hasAuthority('order:read')")
public OrderView getOrder(UUID orderId, CustomerId customerId) {
    return orderQuery.findOwnedBy(orderId, customerId);
}
```

Phải kiểm tra ownership ở service/query boundary, không chỉ ẩn nút trên frontend. Với admin, tách quyền đọc, điều chỉnh Inventory, refund hoặc export dữ liệu. Với domain này, authorization matrix tối thiểu nên bao gồm Product browse, Order create, Order detail, Inventory read và Inventory Adjustment.

Đừng đưa toàn bộ `Authentication` hoặc entity nhạy cảm xuống log. Log `subject`, action, resource type/id đã được làm mờ, decision và correlation id; không log access token, password, PII không cần thiết.

## 7. Cấu hình nền Spring Security nên học bằng test

Dependency thường dùng trong ứng dụng MVC API:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

Với JWT từ IdP:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

Tên starter có thể thay đổi theo major version; luôn tạo dependency từ Spring Initializr hoặc kiểm tra BOM/version của dự án. Cấu hình tối thiểu phải trả lời: endpoint public nào, endpoint nào cần authentication, authority lấy từ claim nào, session stateless hay stateful, CSRF strategy, CORS origins và cách xử lý `401/403`.

Ví dụ skeleton để học, không nên copy nguyên xi vào production:

```java
@Bean
SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.disable()) // chỉ hợp lệ khi threat model không dùng cookie auth
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health/**", "/api/products").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/orders").hasAuthority("order:create")
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
        .build();
}
```

Phần quan trọng hơn đoạn config là test:

```java
mockMvc.perform(post("/api/orders")
        .with(jwt().authorities(new SimpleGrantedAuthority("order:create")))
        .contentType(APPLICATION_JSON)
        .content(validOrderJson))
    .andExpect(status().isCreated());

mockMvc.perform(get("/api/orders/{id}", anotherCustomerOrder))
    .andExpect(status().isForbidden());
```

Test phải bao gồm unauthenticated, authenticated-but-forbidden, correct role/scope, ownership mismatch, expired/invalid token, CSRF nếu dùng cookie, CORS policy và actuator exposure. Spring Security Test hỗ trợ các request post-processors và annotation để tạo authentication giả lập trong test; hãy dùng nó để kiểm tra policy, không bỏ security filter khỏi toàn bộ integration test.

## 8. Danh mục Spring: đừng học theo tên, học theo problem

Spring công bố nhiều project độc lập; Spring Boot là lớp tạo ứng dụng, auto-configuration và dependency management, không phải tên gọi của mọi project. [Spring Projects](https://spring.io/projects)

| Problem | Project/starter nên tìm hiểu | Khi dùng |
|---|---|---|
| HTTP MVC/REST | Spring Web MVC / `spring-boot-starter-webmvc` | API đồng bộ trên Servlet stack |
| Reactive HTTP | Spring WebFlux / `spring-boot-starter-webflux` | Streaming/reactive end-to-end; không thêm chỉ vì “nhanh hơn” |
| Validation | `spring-boot-starter-validation` | Validate input/DTO và constraint nghiệp vụ ở boundary |
| Relational data | Spring Data JPA + JDBC | Repository/ORM hoặc SQL rõ ràng; đo N+1 và query plan |
| Schema migration | Flyway/Liquibase starter | Version-control schema, rollback/compatibility plan |
| Health/metrics | Actuator | Health, metrics, info; expose endpoint có allow-list |
| Authentication/authorization | Spring Security | Web/API security, method security, resource server |
| Database cache/coordination | Spring Data Redis | Cache, rate limit, coordination; không thay source of truth tùy tiện |
| Event streaming | Spring Kafka / Spring AMQP | Kafka hoặc RabbitMQ; chọn theo delivery semantics |
| Distributed app support | Spring Cloud | Config, gateway, discovery, resilience; chỉ dùng khi có boundary thật |
| Modular monolith | Spring Modulith | Kiểm tra module/domain event khi monolith có boundary rõ |
| Batch | Spring Batch | Job lớn, restart, chunk, skip/retry và metadata |
| GraphQL | Spring for GraphQL | Client cần query graph và schema GraphQL |
| SOAP | Spring Web Services | Tích hợp contract-first SOAP legacy |
| AI integration | Spring AI | Kết nối model/vector store/tool; không phải thay thế Spring Security |
| Authorization server | Spring Authorization Server | Chỉ khi sản phẩm thực sự phát hành token/Identity Provider riêng |

Các project như Spring Integration, Spring Cloud Stream, Spring Session, Spring HATEOAS và Spring REST Docs đều hữu ích nhưng nên học sau khi requirement yêu cầu. Đừng thêm Spring Cloud vào một modular monolith chỉ để có nhiều dependency.

## 9. Starter hiện tại của project này

`backend/pom.xml` hiện dùng MVC, Actuator, JPA, Validation, Flyway, Kafka, PostgreSQL và Testcontainers. Đây là một bộ hợp lý cho domain flash-sale hiện tại: HTTP → application/domain → PostgreSQL, cùng outbox/Kafka cho event. Chưa có Security vì MVP chưa có identity/account thật.

Khi thêm security, hãy làm theo thứ tự:

1. Viết ADR identity source và authorization matrix.
2. Thêm `spring-boot-starter-security` và test filter chain.
3. Nếu dùng IdP ngoài, thêm resource-server starter và validate issuer/audience/signing key.
4. Thêm method/resource authorization cho Order và Inventory Adjustment.
5. Quyết định CSRF/CORS/session strategy theo browser boundary.
6. Thêm security test, audit fields, secret handling và actuator policy.
7. Sau đó mới cân nhắc gateway, Cloud, Redis rate limit hoặc service-to-service identity.

## 10. Cách người mới học đúng

### Giai đoạn 1 — Spring Core/Boot

Hiểu bean, dependency injection, configuration properties, profiles, auto-configuration, lifecycle, MVC request flow và testing. Tạo một API nhỏ có validation, error handler, Actuator và test.

### Giai đoạn 2 — Data và transaction

Học JDBC/JPA, transaction boundary, isolation, migration, N+1, index và integration test bằng PostgreSQL. Làm đúng các lab hiện có trước khi thêm Kafka/Cloud.

### Giai đoạn 3 — Security

Làm một API có `SecurityFilterChain`, password hash hoặc resource server JWT, request authorization, method authorization, CSRF/CORS test và IDOR test. Vẽ request flow trước khi học annotation.

### Giai đoạn 4 — Messaging/operations

Học Kafka/AMQP, retry, duplicate, DLQ, outbox, metrics, tracing và health. Tập trung vào delivery semantics thay vì chỉ biết annotation listener.

### Giai đoạn 5 — Cloud/specialized projects

Chỉ thêm Spring Cloud, Batch, GraphQL, Modulith, AI hoặc Authorization Server khi một requirement hoặc failure buộc phải dùng. Mỗi project mới cần một mini-project, một test lỗi và một câu trả lời trade-off.

### Quy tắc chọn dependency

```text
Requirement cụ thể
→ Spring project giải quyết đúng problem
→ starter tối thiểu
→ đọc auto-configuration/official reference
→ viết test chứng minh behavior
→ đo cost/latency/operational burden
→ chỉ giữ dependency nếu có giá trị
```

## 11. Câu trả lời phỏng vấn mẫu

> “Tôi không coi Spring Security là một annotation gắn vào controller. Tôi phân biệt authentication, authorization, resource ownership, CSRF/CORS, secret và audit. Trong Servlet stack, request đi qua SecurityFilterChain; authentication tạo SecurityContext, sau đó request/method authorization quyết định có được vào business flow hay không. Tôi chọn session, JWT resource server hay OIDC dựa trên client boundary và identity provider, không theo tutorial.
>
> Với Spring ecosystem, tôi chọn theo problem: Boot/Web/Validation cho HTTP, Data JPA/JDBC cho persistence, Flyway cho schema, Actuator cho operations, Security cho identity/policy, Kafka cho event delivery. Cloud, Batch, GraphQL, Modulith và Spring AI chỉ thêm khi requirement cần. Tôi luôn bắt đầu bằng dependency tối thiểu, đọc auto-configuration, viết integration/security test và đo operational cost.”

## 12. Checklist tự kiểm tra

- [ ] Phân biệt authentication, authorization và ownership.
- [ ] Giải thích được `SecurityFilterChain`, `Authentication`, `SecurityContext`, `401` và `403`.
- [ ] Biết khi nào session cần CSRF và vì sao bearer token không tự động giải quyết mọi security.
- [ ] Có test IDOR, role/scope, expired token và forbidden action.
- [ ] Không log token/password/PII không cần thiết.
- [ ] Biết chọn MVC hay WebFlux theo execution model.
- [ ] Biết chọn JPA hay JDBC theo query/boundary, không theo hype.
- [ ] Biết Kafka/AMQP khác nhau về delivery và operational trade-off.
- [ ] Không thêm Spring Cloud/Authorization Server nếu chưa có requirement.
- [ ] Có ADR cho identity source và authorization matrix trước khi thêm security vào MVP.
