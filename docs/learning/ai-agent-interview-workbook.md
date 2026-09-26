# Sổ luyện phỏng vấn: Claude Code, Codex và AI agent trong engineering

Tài liệu này giúp biến việc dùng AI agent thành câu trả lời có bằng chứng. Người phỏng vấn thường không cần nghe danh sách command; họ muốn biết bạn có kiểm soát yêu cầu, context, chất lượng, bảo mật và trách nhiệm vận hành hay không.

## 1. Câu trả lời 60 giây

> “Tôi dùng Claude Code hoặc Codex như một coding agent trong SDLC, không coi nó là người thay thế developer. Tôi bắt đầu bằng issue có acceptance criteria, invariant và lệnh kiểm chứng. Agent đọc repository, ADR và instruction file như `CLAUDE.md` hoặc `AGENTS.md`, sau đó tạo plan; tôi review plan trước khi cho phép sửa.
>
> Tôi chia feature thành các task nhỏ trên branch/worktree, dùng agent cho điều tra code, boilerplate, test skeleton, debug và review lặp lại. Với task độc lập thì chạy song song, còn thay đổi cùng một invariant thì giữ tuần tự. Agent chỉ được cấp quyền tối thiểu và không có production secret.
>
> Sau mỗi thay đổi, tôi chạy formatter, lint, typecheck, unit/integration/concurrency test tùy rủi ro, rồi tự review diff ở các điểm như transaction, authorization, migration và retry. PR vẫn đi qua CI, code review và approval của con người. Tôi đo hiệu quả bằng lead time, review iterations, escaped defects, test pass rate và chi phí token. AI tăng tốc vòng lặp feedback; developer vẫn chịu trách nhiệm về kiến trúc, correctness và release.”

Đây là câu trả lời tốt vì nó có workflow, boundary, verification và metric. Nó cũng phù hợp với hướng dẫn chính thức về project context của Claude Code và context/skills của Codex. [Claude Code memory](https://docs.anthropic.com/en/docs/claude-code/memory) · [OpenAI Codex guide](https://developers.openai.com/learn/codex)

## 2. Câu trả lời hai phút về triển khai thực tế

Khi interviewer hỏi “cụ thể bạn setup thế nào?”, hãy đi theo sáu bước:

1. **Bootstrap context.** Đưa kiến trúc, domain glossary, lệnh build/test, coding rules, ADR và definition of done vào instruction files. Context nên ngắn, có link tới tài liệu chi tiết và được version-control.
2. **Define the task contract.** Issue phải có mục tiêu, phạm vi, acceptance criteria, invariant, non-goals, test plan và rollback condition.
3. **Plan before edit.** Cho agent khảo sát code và lập plan; người phát triển duyệt plan, đặc biệt với schema, transaction, public API và concurrency.
4. **Implement in bounded loops.** Chia vertical slice nhỏ, chạy branch/worktree riêng, giữ task độc lập mới chạy song song. Agent trả về diff, test output và câu hỏi còn thiếu sau mỗi loop.
5. **Verify by risk.** Happy path, failure path, integration boundary, load/concurrency và security check được chọn theo rủi ro, không theo thói quen.
6. **Gate delivery and learn.** CI, review, approval và rollback bảo vệ release. Sau task, cập nhật instruction/ADR nếu có kiến thức đã xác minh; đo thời gian và defect để biết agent thực sự tạo giá trị.

Claude Code có permission mode và allow/disallow tool; Codex/agent workflows cũng nên đặt approval tại ranh giới side effect. OpenAI mô tả guardrail tự động và human review là hai lớp bổ sung, trong đó các hành động nhạy cảm phải dừng để người có thẩm quyền duyệt. [Claude Code CLI](https://docs.anthropic.com/en/docs/claude-code/cli-usage) · [OpenAI guardrails and human review](https://developers.openai.com/api/docs/guides/agents/guardrails-approvals)

## 3. Bộ câu hỏi thường gặp và câu trả lời mẫu

### “Bạn dùng AI agent ở giai đoạn nào của SDLC?”

> “Tôi dùng ở discovery để map code và tìm rủi ro, ở planning để tạo task/test plan, ở implementation cho thay đổi lặp lại, ở testing để sinh case và phân tích failure, ở review để tìm missing tests/security issue, và ở maintenance để điều tra regression. Tôi không dùng agent để tự quyết yêu cầu chưa rõ hoặc tự deploy production.”

### “Làm sao đảm bảo agent không viết code sai?”

> “Tôi không xem output của agent là bằng chứng. Tôi đặt acceptance criteria và invariant trước, yêu cầu agent nêu file đã đọc, plan và giả định, sau đó dùng test, integration test, benchmark và diff review để falsify implementation. Nếu test pass nhưng invariant chưa được chứng minh, task vẫn chưa hoàn thành.”

### “Bạn prompt agent như thế nào?”

> “Prompt của tôi gồm context cần đọc, mục tiêu, non-goals, constraints, output format và verification command. Tôi yêu cầu agent nói rõ assumption và dừng khi thiếu thông tin. Quy tắc lặp lại đưa vào instruction file; prompt của issue chỉ giữ phần đặc thù của task.”

### “Khi nào dùng sub-agent hoặc chạy song song?”

> “Chỉ khi các workstream độc lập hoặc cần context cô lập, chẳng hạn một agent đọc database plan và một agent kiểm tra API contract. Nếu cùng sửa một invariant hoặc cần trao đổi state liên tục, chạy tuần tự sẽ dễ kiểm soát hơn. Tôi so sánh lợi ích parallelism với chi phí context, merge conflict và review.”

### “Bạn xử lý bảo mật và dữ liệu nhạy cảm ra sao?”

> “Tôi áp dụng least privilege, không đưa secret hoặc dữ liệu Customer thật vào prompt, tách dev credentials, giới hạn MCP và yêu cầu approval cho shell command, migration, network call hoặc production action. Tôi review permission rules định kỳ và kiểm tra những gì agent thực sự đã đọc.”

### “Claude Code và Codex khác nhau thế nào trong workflow?”

> “Tôi không chọn theo thương hiệu. Tôi chọn theo boundary và workflow: công cụ nào đọc được repository, chạy test, sửa file, dùng context/skill và tạo evidence tốt hơn cho task đó. Claude Code mạnh ở terminal-centric workflow với project memory, permission và MCP; Codex phù hợp khi tôi cần thread/goal bền, worktree hoặc workflow gắn với review và automation trong hệ sinh thái OpenAI. Nguyên tắc SDLC, verification và human gate vẫn giữ nguyên.”

Khi nói câu này, tránh khẳng định tính năng cố định nếu bạn chưa kiểm tra phiên bản đang dùng. Hãy mô tả capability và evidence thay vì so sánh model bằng cảm tính.

### “Bạn biết hoặc đang dùng plugin, skill và MCP nào?”

> “Tôi phân biệt ba lớp. Plugin là gói phân phối có thể bao gồm skill, MCP và cấu hình. Skill là quy trình có thể tái sử dụng, chẳng hạn `run-quality-gates`, `review-pr` hoặc `incident-triage`. MCP là lớp kết nối agent với dữ liệu và công cụ bên ngoài.
>
> Bộ tôi ưu tiên là Docs MCP để tra cứu tài liệu theo version, GitHub MCP read-only để lấy issue/PR context, Playwright MCP để chạy browser smoke test và Sentry MCP read-only để điều tra lỗi. Với thư viện thay đổi nhanh, tôi có thể dùng Context7 nhưng vẫn kiểm tra tài liệu upstream và pin version. Database MCP chỉ được kết nối đến local/staging với dataset synthetic.
>
> Tôi không cài tất cả server nổi tiếng. Mỗi MCP phải có owner, phạm vi dữ liệu, permission, logging và lý do đo được. Skill quy định thứ tự gọi tool, output artifact và stop condition; write action như sửa issue, gửi message, migration hoặc deploy cần approval của con người.”

OpenAI có Docs MCP chính thức cho Codex và Claude Code; Playwright MCP do Microsoft duy trì; Sentry có MCP cho debug; Context7 tập trung vào tài liệu thư viện theo version. Đây là các ví dụ để nói trong phỏng vấn, không phải danh sách cần cài đồng loạt. [OpenAI Docs MCP](https://developers.openai.com/learn/docs-mcp) · [Playwright MCP](https://github.com/microsoft/playwright-mcp) · [Sentry MCP](https://github.com/getsentry/sentry-mcp) · [Context7](https://github.com/upstash/context7)

Nếu interviewer hỏi “MCP có nguy hiểm không?”, trả lời: “Có, nếu cấp write, network hoặc secret quá rộng. Tôi bắt đầu bằng read-only, allowlist server, isolated environment và synthetic data; mỗi write tool có approval, audit và rollback. MCP mở capability, còn skill chỉ điều phối quy trình — skill không làm server an toàn hơn nếu permission sai.”

### “Bạn đo năng suất như thế nào?”

> “Tôi đo baseline trước pilot và so sánh cùng loại task: median issue-to-PR time, time-to-first-test, review iterations, CI failure rate, escaped defects, rework rate và chi phí token. Tôi tách thời gian được giảm nhờ boilerplate khỏi thời gian tăng thêm để review agent output. Nếu cycle time giảm nhưng defect tăng, đó không phải năng suất tốt.”

### “AI tạo ra thay đổi nguy hiểm thì sao?”

> “Agent phải dừng ở permission/approval boundary. Tôi giữ branch riêng, chạy test và review diff trước merge, không cho agent tự đọc production secret hoặc tự deploy. Nếu phát hiện sai, tôi rollback branch, tạo reproduction và cập nhật rule/test để lần sau không lặp lại.”

## 4. Tình huống gây ấn tượng: flash-sale inventory

### Đề bài

“Hãy dùng AI agent để triển khai API tạo Order cho Product giới hạn số lượng.”

### Cách trả lời

> “Tôi bắt đầu bằng invariant: quantity từ 1 đến 5, Available Quantity không âm, retry không tạo Order trùng, giá được chốt khi Order được chấp nhận, và Order Created phải đi cùng inventory deduction. Tôi cho agent đọc `CONTEXT.md`, ADR về atomic conditional update, ADR về transactional outbox, OpenAPI contract và integration tests.
>
> Tôi yêu cầu plan trước, trong đó phải nêu transaction boundary, affected-row outcome, idempotency và failure path. Sau khi duyệt, agent triển khai adapter và application flow trên branch riêng, đồng thời tạo PostgreSQL integration test cho concurrent requests. Tôi không chấp nhận mock thay cho database khi cần chứng minh atomicity.
>
> Sau đó tôi yêu cầu agent review riêng về overselling, duplicate retry, transaction, event delivery và API compatibility. Tôi tự kiểm tra SQL, index, migration và retry semantics, rồi chạy load test. PR phải có test output, benchmark, risk và rollback note. Merge vẫn qua CI và reviewer.”

### Follow-up có thể bị hỏi

**“Nếu test đều xanh nhưng vẫn oversell?”**

> “Có thể test chưa chạy concurrency trên PostgreSQL thật, hoặc assertion chỉ kiểm tra response mà không kiểm tra tổng accepted quantity và Available Quantity cuối. Tôi sẽ tạo concurrent reproduction, kiểm tra affected-row update và transaction isolation, sau đó biến failure thành test hồi quy.”

**“Tại sao không để agent dùng pessimistic lock cho an toàn?”**

> “Lock không tự động là lựa chọn tốt. Với invariant đơn giản, atomic conditional update có thể giữ lock ngắn hơn và dễ đo hơn; tôi chọn theo ADR và benchmark. Nếu workload hoặc invariant thay đổi, tôi so sánh optimistic/pessimistic/atomic bằng correctness, throughput, p95, retry rate và lock wait.”

## 5. Prompt templates để luyện

### Reconnaissance

```text
Read the repository, relevant ADRs, domain glossary, API contract, and tests.
Do not edit files.
Return current flow, invariants, likely change surface, unknowns, and a focused
verification plan. Quote file paths and line numbers for every important claim.
```

### Implementation

```text
Implement only the approved plan.
Do not change public contracts or add abstractions outside the scope.
Work in small steps. After each step, run the focused test and inspect the diff.
If the plan conflicts with an ADR or domain invariant, stop and report it.
```

### Review

```text
Review this diff as a skeptical senior engineer.
Find only actionable issues. Check correctness, concurrency, failure paths,
security, performance, API compatibility, migration safety, and missing tests.
For each issue return severity, file/line, evidence, and a verification step.
```

### Performance

```text
Do not optimize by intuition.
First define the workload and baseline p50/p95/p99, throughput, error rate,
CPU, memory, database calls, and lock wait. Propose hypotheses, run a focused
benchmark, change one variable, and report before/after plus regressions.
```

## 6. Thang điểm tự luyện

Tự ghi âm câu trả lời và chấm 0–2 điểm cho mỗi mục:

| Mục | 0 điểm | 1 điểm | 2 điểm |
|---|---|---|---|
| SDLC | chỉ nói code generation | nói vài bước rời rạc | mô tả từ issue đến release |
| Context | không có strategy | có prompt dài | có instruction/ADR/domain context |
| Quality | tin output agent | chạy test cơ bản | risk-based verification + human review |
| Security | cho toàn quyền | nhắc sơ qua secret | least privilege, approval, MCP boundary |
| Parallelism | luôn chạy nhiều agent | biết có thể xung đột | nêu tiêu chí độc lập và merge cost |
| Productivity | nói “nhanh hơn” | có một ví dụ | có baseline, metrics và defect trade-off |
| Domain | ví dụ chung chung | biết API/database | nêu invariant flash sale và failure path |
| Ownership | agent chịu trách nhiệm | developer review | developer sở hữu architecture, correctness, release |

Mục tiêu là ít nhất 12/16 điểm. Nếu dưới mức này, quay lại một feature thật trong repository và tạo evidence thay vì học thêm thuật ngữ.

## 7. Portfolio evidence cần chuẩn bị

- Một `CLAUDE.md` hoặc `AGENTS.md` ngắn, có architecture, commands và invariants.
- Một issue trước/sau khi dùng agent.
- Một plan agent tạo ra và phần bạn đã chỉnh sửa.
- Một diff có test output và review findings.
- Một failure do agent tạo ra, reproduction và regression test.
- Một benchmark hoặc metric before/after.
- Một ghi chú về permission, MCP, secret và rollback.
- Một PR summary có trade-off, phần agent làm tốt và phần agent làm sai.

## 8. Các câu không nên nói

- “Tôi chỉ cần prompt đúng là agent làm hết.”
- “Test xanh nghĩa là code chắc chắn đúng.”
- “Tôi cho agent quyền toàn bộ để nhanh hơn.”
- “Sub-agent càng nhiều càng tốt.”
- “AI giúp tôi tăng năng suất 10 lần” nhưng không có baseline.
- “Claude/Codex tự chịu trách nhiệm nếu có lỗi.”

Thay vào đó, hãy nói: **“Tôi dùng agent để giảm thời gian đọc, viết lặp lại và feedback; tôi dùng test, CI, review, permission và metrics để giữ chất lượng.”**

## 9. Bài tập mô phỏng phỏng vấn

1. Trình bày câu trả lời 60 giây, không dùng từ “vibe coding”.
2. Giải thích cách setup repository trong 2 phút.
3. Nhận đề bài “no overselling” và nói rõ invariant trước khi nhắc đến công cụ.
4. Bị phản biện “AI làm code kém an toàn hơn” và trả lời bằng permission, tests, review và metrics.
5. Bị hỏi “nếu agent sai liên tục?” và mô tả reproduction → rule/context update → regression test.
6. Kết thúc bằng một metric cụ thể bạn sẽ theo dõi trong pilot đầu tiên.
