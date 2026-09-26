# AI agent trong vòng đời phát triển phần mềm

Tài liệu này dùng để học và luyện triển khai Claude Code, Codex hoặc coding agent tương tự trong một codebase thật. Mục tiêu không phải là để agent tự viết càng nhiều code càng tốt. Mục tiêu là rút ngắn feedback loop từ yêu cầu đến bằng chứng rằng thay đổi đúng, an toàn và có thể vận hành.

Nếu bạn mới làm quen Claude Code, hãy đọc [bài học AI-native SDLC cho người mới](../visual-guides/claude_code_ai_native_sdlc_beginner_guide.md) trước. Bài đó giải thích cùng quy trình bằng một feature flash-sale từ `intent.md` đến production metrics.

## 1. Mô hình tư duy

Hãy xem coding agent là một thành viên kỹ thuật có khả năng đọc repository, chạy công cụ và tạo thay đổi, nhưng chưa có quyền tự quyết về nghiệp vụ hay rủi ro production.

```text
Yêu cầu rõ → context đúng → kế hoạch có review → thay đổi nhỏ
→ kiểm chứng tự động → review con người → CI/release → học lại vào repository
```

Bốn lớp cần thiết:

| Lớp | Câu hỏi phải trả lời | Ví dụ bằng chứng |
|---|---|---|
| Context | Agent cần biết gì để không đoán? | `CLAUDE.md`, `AGENTS.md`, ADR, API contract |
| Execution | Agent được phép làm gì và trong phạm vi nào? | branch/worktree, permission, MCP allow-list |
| Verification | Làm sao biết kết quả đúng? | test, lint, typecheck, benchmark, diff review |
| Governance | Ai chịu trách nhiệm và thay đổi được audit thế nào? | PR, CI, approval, log, rollback plan |

Claude Code có cơ chế memory dự án qua `CLAUDE.md`; Codex có thể dùng `AGENTS.md` và các skill/instruction theo workflow. Các file này nên chứa quy ước và lệnh có giá trị lặp lại, không phải một prompt dài kể toàn bộ lịch sử dự án. [Claude Code memory](https://docs.anthropic.com/en/docs/claude-code/memory) · [Codex context and skills](https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra)

## 2. Chuẩn hóa repository trước khi dùng agent

### Context tối thiểu

Đặt một file hướng dẫn ở root repository, sau đó liên kết tới tài liệu chi tiết:

```markdown
# Project instructions

## Product invariants
- Product có giá hiện tại; Order lưu giá tại thời điểm chấp nhận.
- Available Quantity không được âm.
- Một request retry không được tạo hai Order.
- Order Created và inventory deduction commit trong cùng transaction.

## Architecture
- Modular monolith, package-by-feature.
- API/infrastructure phụ thuộc vào application/domain.
- PostgreSQL là source of truth cho Product, Inventory và Order.

## Commands
- Backend tests: `cd backend; .\mvnw.cmd test`
- Frontend checks: `cd frontend; npm test; npm run typecheck; npm run build`
- API contract: `npx --yes @redocly/cli lint openapi/openapi.yaml`

## Working rules
- Đọc code và test hiện có trước khi sửa.
- Không đổi public contract nếu issue chưa yêu cầu.
- Không dùng production credentials.
- Mọi thay đổi nghiệp vụ phải có test cho failure path.
```

Với dự án này, các invariant phải dùng đúng từ vựng trong [`CONTEXT.md`](../../CONTEXT.md): Product, Inventory, Available Quantity, Order, Customer, Inventory Adjustment và Order Created. Những quyết định về atomic inventory decrement và transactional outbox phải được đọc từ [`docs/adr/`](../adr/) trước khi agent đề xuất thay đổi.

### Chính sách quyền và dữ liệu

Thiết lập theo nguyên tắc quyền tối thiểu:

- Cho phép đọc repository, chạy formatter và các lệnh test an toàn.
- Yêu cầu approval cho ghi file ngoài worktree, migration, xóa dữ liệu, gọi API bên ngoài hoặc deploy.
- Không đưa API key, token, dữ liệu Customer thật hoặc production database vào context.
- Chỉ kết nối MCP server được đội ngũ tin cậy và ghi rõ mục đích, dữ liệu được đọc và thao tác được phép.
- Không bật chế độ bỏ qua toàn bộ permission trong môi trường phát triển thông thường.

Claude Code cung cấp permission mode và allow/disallow tool; tài liệu CLI cũng cảnh báo việc bỏ qua permission. OpenAI mô tả approval và guardrail là điểm dừng trước side effect như chỉnh sửa, shell command hoặc MCP nhạy cảm. [Claude Code CLI](https://docs.anthropic.com/en/docs/claude-code/cli-usage) · [OpenAI guardrails and human review](https://developers.openai.com/api/docs/guides/agents/guardrails-approvals)

## 3. Quy trình chuẩn cho một issue

### Bước 1 — Viết issue có thể kiểm chứng

Một issue tốt phải có:

- mục tiêu người dùng hoặc business;
- acceptance criteria;
- phạm vi file/module được phép đụng tới;
- invariant không được phá;
- test hoặc benchmark chứng minh hoàn thành;
- rủi ro và điều kiện rollback nếu thay đổi có side effect.

Ví dụ:

```text
Implement atomic order acceptance.

Acceptance criteria:
- Quantity chỉ từ 1 đến 5.
- Available Quantity không âm khi có concurrent requests.
- Nếu conditional update ảnh hưởng 0 row, trả lỗi insufficient inventory.
- Order Created và inventory deduction commit cùng transaction.
- Có PostgreSQL integration test cho 50 request đồng thời.

Do not:
- đổi API contract ngoài phạm vi issue;
- dùng lock phân tán mới;
- gọi Kafka trên critical path của order request.
```

### Bước 2 — Cho agent điều tra trước, chưa sửa ngay

Prompt nên yêu cầu agent tạo một plan có các phần: hiện trạng, file liên quan, invariant, phương án, test plan, rủi ro và câu hỏi còn thiếu. Người phát triển duyệt plan trước khi bật quyền chỉnh sửa.

```text
Inspect the repository and the relevant ADRs before proposing a change.
Do not edit files yet.
Return:
1. current flow and data boundaries;
2. invariants that must remain true;
3. smallest implementation plan;
4. tests that can falsify the plan;
5. risks, rollback, and unresolved questions.
```

Điều này tránh hai lỗi phổ biến: agent sửa file đầu tiên nó nhìn thấy và tạo giải pháp đúng test nhưng sai domain.

### Bước 3 — Tách thành vertical slice nhỏ

Mỗi task nên đi từ một thay đổi nhỏ đến một bằng chứng chạy được:

```text
contract/schema → application flow → adapter → test → observability
```

Chỉ chạy song song những việc độc lập như đọc API contract, nghiên cứu query plan và viết test case. Không chia song song các task cùng sửa một invariant hoặc cùng một file. Sub-agent làm tăng tốc khi context có thể tách biệt; task nhỏ, tuần tự hoặc cần giữ cùng context nên chạy trực tiếp.

### Bước 4 — Implement với vòng lặp ngắn

Một vòng lặp nên có dạng:

```text
read → propose → edit → run focused test → inspect diff → continue
```

Không nên đưa cả epic vào một prompt duy nhất. Sau mỗi vòng, lưu lại kết quả vào plan hoặc issue để phiên sau không phải nạp lại toàn bộ hội thoại.

### Bước 5 — Verification nhiều tầng

Agent có thể chạy lệnh, nhưng developer phải thiết kế bằng chứng:

| Tầng | Mục tiêu | Ví dụ trong repository |
|---|---|---|
| Format/lint/typecheck | bắt lỗi cơ bản | Maven/Java checks, TypeScript typecheck |
| Unit test | logic thuần | quantity validation, idempotency decision |
| Integration test | boundary thật | PostgreSQL, Testcontainers, outbox |
| Concurrency/load test | invariant dưới tải | atomic decrement, no oversell |
| Contract/security check | không phá client và boundary | OpenAPI lint, authorization, secret scan |
| Human review | trade-off và domain | transaction, index, retry, rollback |

Đừng dùng “test xanh” làm định nghĩa duy nhất của correctness. Với flash sale, test phải kiểm tra accepted quantity và Available Quantity cuối cùng, không chỉ kiểm tra response status.

### Bước 6 — PR, CI và release

Agent nên tạo được:

- summary thay đổi;
- danh sách file và lý do;
- test command cùng output tóm tắt;
- risk/rollback note;
- câu hỏi cần reviewer quyết định.

CI là cổng bắt buộc. Agent có thể tự sửa lỗi test trong branch, nhưng merge và deploy vẫn cần policy của đội. Với automation, Codex CLI/SDK có thể được gọi từ script hoặc pipeline; OpenAI khuyến nghị thiết kế tool, approval và observability quanh agent thay vì để model tự quyết side effect. [Codex learning guide](https://developers.openai.com/learn/codex) · [Building consistent Codex workflows](https://developers.openai.com/cookbook/examples/codex/codex_mcp_agents_sdk/building_consistent_workflows_codex_cli_agents_sdk)

## 4. Các workflow có ROI cao

| Nút thắt | Agent làm | Con người giữ quyền quyết định | Chỉ số đo |
|---|---|---|---|
| Đọc code mất nhiều thời gian | map module, trace call, tìm test/ADR | chọn boundary đúng | time-to-plan |
| Boilerplate lặp lại | tạo DTO, mapper, test skeleton, migration draft | schema và public contract | time-to-first-PR |
| Debug khó tái hiện | đọc log, tạo reproduction, đề xuất hypothesis | xác nhận nguyên nhân | time-to-reproduce |
| Review chậm | kiểm tra diff, edge case, missing tests | merge decision | review iterations |
| Regression | sinh test từ acceptance criteria | test oracle và fixture | escaped defects |
| Tài liệu lỗi thời | cập nhật README/ADR/runbook sau thay đổi | nội dung quyết định | stale-doc findings |
| Tối ưu mù | viết benchmark, chạy EXPLAIN/load test | chọn trade-off | p95, throughput, cost |

Không nên tuyên bố phần trăm tiết kiệm nếu chưa đo. Hãy đo baseline trong một sprint, thử trên một nhóm task, rồi so sánh lead time, review time, escaped defect rate, test pass rate và token cost.

## 5. Plugin, skill và MCP nên biết

Ba khái niệm này khác nhau:

| Thành phần | Vai trò | Ví dụ |
|---|---|---|
| Plugin | Gói phân phối gồm skill, MCP, cấu hình và đôi khi connector | OpenAI Developers plugin |
| Skill | Quy trình có thể tái sử dụng, thường có `SKILL.md`, input/output và stop condition | `review-pr`, `run-tests`, `incident-triage` |
| MCP | Giao thức kết nối agent với dữ liệu hoặc tool bên ngoài | Docs, GitHub, Playwright, Sentry, database |

Skill không thay thế MCP: MCP cung cấp khả năng hoặc dữ liệu, còn skill quy định khi nào gọi, gọi theo thứ tự nào và kiểm chứng kết quả ra sao. Tài liệu OpenAI mô tả skill là thư mục instruction và supporting files; MCP cung cấp resources, prompts và tools. [OpenAI Skills](https://developers.openai.com/plugins/concepts/skills) · [MCP server concepts](https://github.com/modelcontextprotocol/modelcontextprotocol/blob/main/docs/docs/2026-07-28/learn/server-concepts.mdx)

### Bộ khởi đầu phù hợp với repository này

| Ưu tiên | Công cụ | Cách dùng có giá trị | Ranh giới cần nói rõ |
|---|---|---|---|
| 1 | OpenAI Developer Docs MCP | Tra cứu API/Codex/Agents docs theo phiên bản, yêu cầu agent trích nguồn | Read-only; không gọi OpenAI API thay cho ứng dụng |
| 2 | GitHub MCP Server | Đọc issue, PR, diff, review comment và liên kết task với evidence | Token scope nhỏ; write action như comment/merge phải approval |
| 3 | Microsoft Playwright MCP | Chạy browser smoke/e2e, kiểm tra form, auth flow và screenshot | Dùng staging/isolated profile; không dùng cookie tài khoản thật |
| 4 | Sentry MCP | Điều tra error, trace và release regression từ staging hoặc production read-only | Không cho agent tự sửa alert, deploy hoặc gửi thông báo |
| 5 | Context7 | Lấy tài liệu API đúng version của Spring, React, Vite hoặc thư viện mới | Luôn pin version và đối chiếu tài liệu upstream; nội dung cộng đồng vẫn phải review |
| 6 | PostgreSQL/Redis MCP nội bộ | Đọc schema, query plan và dữ liệu synthetic trong local/staging | Mặc định read-only; chặn mutation và không đưa PII vào context |

OpenAI có Docs MCP chính thức tại `https://developers.openai.com/mcp`; tài liệu hướng dẫn cách cấu hình cho Codex và Claude Code. Microsoft Playwright MCP cung cấp browser automation qua accessibility snapshot. Sentry duy trì MCP cho workflow debug. Context7 cung cấp docs theo version nhưng chính họ cũng khuyến cáo kiểm tra chất lượng nội dung. [OpenAI Docs MCP](https://developers.openai.com/learn/docs-mcp) · [Playwright MCP](https://github.com/microsoft/playwright-mcp) · [Sentry MCP](https://github.com/getsentry/sentry-mcp) · [Context7](https://github.com/upstash/context7)

Với project này, nên bắt đầu bằng ba kết nối: Docs MCP, GitHub read-only và Playwright trên local/staging. Chỉ thêm Sentry khi đã có observability; chỉ thêm database MCP sau khi tạo dataset synthetic và policy chặn write. Không cài hàng chục server cùng lúc vì mỗi server tăng context, tool surface và chi phí review.

### Những skill nên tự xây trong repository

- `recon-repository`: đọc `CONTEXT.md`, ADR, module và test rồi xuất dependency map.
- `run-quality-gates`: chạy đúng backend/frontend/contract checks và lưu output.
- `review-flash-sale`: kiểm tra oversell, idempotency, transaction, outbox và failure path.
- `review-migration`: kiểm tra backward compatibility, lock duration, rollback và dữ liệu cũ.
- `benchmark-inventory`: chạy concurrency/load test và báo correctness, throughput, p95, lock wait.
- `incident-triage`: đọc log/trace read-only, tạo hypothesis, reproduction và next action.

Mỗi skill cần ghi rõ input, output artifact, tool được phép dùng, điều kiện dừng và lệnh kiểm chứng. Skill tốt làm giảm thời gian hướng dẫn lặp lại; skill quá dài làm tăng context và khiến agent khó chọn đúng quy trình.

Repository này đã có một chuỗi skill thực tế trong README: `/teach` → `/grill-with-docs` → `/to-spec` → `/to-tickets` → `/tdd` hoặc `/implement` → `/code-review` → `/handoff`. Khi nói trong phỏng vấn, hãy giải thích artifact ở mỗi bước: kiến thức/invariant, requirement đã làm rõ, spec, ticket nhỏ, diff + test, review findings và handoff state. Như vậy người nghe thấy đây là một delivery system có checkpoint, không phải tập lệnh rời rạc.

### Cách trả lời phỏng vấn về bộ công cụ

> “Tôi phân biệt rõ plugin, skill và MCP. Plugin là gói phân phối; skill mã hóa quy trình; MCP mở quyền truy cập dữ liệu hoặc hành động. Trong dự án, tôi dùng Docs MCP để tra cứu version chính thức, GitHub MCP read-only để lấy issue/PR context, Playwright MCP để kiểm tra browser flow và Sentry MCP để điều tra lỗi. Các skill như `review-flash-sale` và `run-quality-gates` quy định thứ tự gọi, evidence và stop condition. Mọi write tool, production data và deploy đều có allowlist và human approval.”

Đây là câu trả lời tốt hơn việc đọc danh sách tên server, vì nó cho thấy bạn hiểu capability, workflow và risk boundary.

## 6. Ví dụ xuyên suốt: giữ hàng trong flash sale

### Context

Feature là chấp nhận Order cho một Product với quantity từ 1 đến 5. Invariant là không oversell, retry không tạo Order trùng, giá được chốt tại thời điểm chấp nhận và Order Created phải đi cùng inventory deduction.

### Cách dùng agent

1. Cho agent đọc `CONTEXT.md`, ADR về atomic conditional update, ADR về transactional outbox, API contract và test hiện tại.
2. Yêu cầu plan chỉ ra transaction boundary, affected-row outcome, idempotency key và failure path.
3. Cho agent triển khai adapter conditional update và application flow trên branch riêng.
4. Yêu cầu tạo PostgreSQL integration test và concurrent test; không chấp nhận mock thay cho database ở phần chứng minh atomicity.
5. Dùng agent review để tìm race condition, retry bug, N+1 query và thiếu metric; developer tự đọc SQL và migration.
6. Chạy benchmark trước/sau, mở PR cùng evidence và rollback note.

### Prompt kiểm tra chất lượng

```text
Review this change as a senior backend reviewer.
Focus on:
- overselling and concurrent requests;
- idempotent retries;
- transaction and outbox boundaries;
- PostgreSQL affected-row handling;
- API compatibility;
- missing integration or failure-path tests.

Do not praise the patch. Return only actionable findings with file, evidence,
severity, and a concrete verification step.
```

## 7. Lỗi triển khai thường gặp

**“Một prompt làm cả feature.”** Kết quả là context quá rộng, diff khó review và test bị viết để khớp implementation. Chia task theo vertical slice.

**“Agent tự chọn kiến trúc.”** Hãy cung cấp ADR và yêu cầu nó nêu trade-off; quyết định vẫn thuộc developer.

**“Chỉ kiểm tra unit test.”** Boundary database, concurrency, retry và event delivery phải có test thật hoặc benchmark phù hợp.

**“Mở quá nhiều MCP.”** Mỗi connector làm tăng context và bề mặt rủi ro. Chỉ bật server cho task cần nó.

**“Tự động hóa không có gate.”** Mọi hành động có side effect phải có permission, approval hoặc CI policy rõ ràng.

**“Không cập nhật context.”** Khi agent lặp cùng lỗi, ghi nguyên nhân và quy tắc đã xác minh vào tài liệu dự án; khi quy tắc cũ không còn đúng, xóa nó.

## 8. Kế hoạch luyện tập bốn tuần

**Tuần 1 — Context và planning.** Tạo `CLAUDE.md`/`AGENTS.md`, viết ba issue có acceptance criteria, yêu cầu agent chỉ điều tra và lập plan. Mỗi bài phải có file liên quan, invariant và test plan.

**Tuần 2 — Implement và test.** Chọn một feature nhỏ, chạy vòng lặp read → plan → edit → test → diff. Ghi lại thời gian thủ công và thời gian có agent.

**Tuần 3 — Review và reliability.** Dùng agent review cho concurrency, failure path, security và performance. Cố ý tạo một bug, yêu cầu agent tái hiện, sau đó viết test bảo vệ.

**Tuần 4 — Interview evidence.** Chọn một feature thật của repository, tạo PR hoặc report gồm before/after, test output, benchmark, risk, rollback và những gì agent làm sai. Dùng report đó để trả lời theo cấu trúc Situation → Decision → Execution → Evidence → Trade-off.

## 9. Checklist trước khi nói “đã áp dụng AI agent”

- [ ] Có instruction/context file ngắn và cập nhật.
- [ ] Issue có acceptance criteria, invariant và verification surface.
- [ ] Agent chạy plan trước khi sửa.
- [ ] Task có boundary và branch/worktree riêng khi cần.
- [ ] Permission/MCP/data policy được cấu hình.
- [ ] Có test cho happy path và failure path.
- [ ] Có review diff của con người.
- [ ] CI vẫn là cổng merge.
- [ ] Có metric baseline và kết quả sau pilot.
- [ ] Có ví dụ agent sai và cách quy trình phát hiện/sửa sai.
