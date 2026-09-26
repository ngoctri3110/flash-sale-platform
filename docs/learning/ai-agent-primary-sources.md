# Nguồn chính thức về AI coding agents: Claude Code và OpenAI Codex

> Tài liệu này là sổ tay nguồn gốc (primary-source notes) để học cách triển khai AI agent trong vòng đời phát triển phần mềm và luyện trả lời phỏng vấn. Các mô tả sản phẩm có thể thay đổi; nên kiểm tra lại tài liệu chính thức trước khi áp dụng.

## 1. Mô hình tư duy dùng khi phỏng vấn

AI coding agent là một vòng lặp gồm **context → plan → execute → verify → review → deliver**. Đây là cách tổng hợp từ các khả năng mà tài liệu chính thức mô tả: agent đọc và sửa workspace, chạy lệnh trong môi trường bị giới hạn, kết nối MCP, dùng skills/subagents, rồi dừng để chờ phê duyệt cho tác động nhạy cảm. [Anthropic: Claude Code security](https://docs.anthropic.com/en/docs/claude-code/security), [OpenAI: Agents API overview](https://developers.openai.com/api/docs/guides/agents-api/overview), [OpenAI: Sandbox agents](https://developers.openai.com/api/docs/guides/agents/sandboxes)

Một câu trả lời phỏng vấn có chất lượng nên phân biệt ba lớp:

- **Model**: suy luận và sinh đề xuất.
- **Harness/agent runtime**: quản lý phiên, context, công cụ, handoff, phê duyệt và trạng thái.
- **Execution environment**: nơi agent đọc/ghi file, chạy test, mở cổng hoặc gọi MCP.

OpenAI mô tả rõ ranh giới harness (control plane) và sandbox (execution plane); ranh giới này giúp tách quyền điều khiển, audit và human review khỏi filesystem, network và lệnh chạy bởi agent. [OpenAI: Sandbox agents](https://developers.openai.com/api/docs/guides/agents/sandboxes), [OpenAI: Agents API architecture](https://developers.openai.com/api/docs/guides/agents-api/architecture)

## 2. Chuẩn hóa context trước khi giao việc

### Claude Code

`CLAUDE.md` là file hướng dẫn dự án dùng cho kiến trúc, quy ước code, lệnh build/test và workflow chung; Claude Code tự nạp các memory file ở đầu phiên. File có thể import tài liệu khác bằng cú pháp `@path`, và tài liệu chính thức khuyên giữ nội dung cụ thể, có cấu trúc, được rà soát định kỳ. [Anthropic: Manage Claude's memory](https://docs.anthropic.com/en/docs/claude-code/memory)

Mẫu tối thiểu nên có:

```md
# Project instructions
- Architecture: modular monolith; domain code does not call adapters directly.
- Commands:
  - build: ./mvnw verify
  - unit tests: ./mvnw test
  - lint: ./mvnw spotless:check
- Invariants:
  - inventory decrement is atomic and cannot oversell
  - payment webhook is idempotent
- Before editing: inspect existing module and tests.
- Before handoff: run formatter, typecheck/lint, unit and integration tests.
```

Nếu cần chạy qua CLI/CI, Claude Code có thể chạy non-interactive bằng `-p`, giới hạn số lượt bằng `--max-turns`, chọn output `text/json/stream-json`, và giới hạn công cụ bằng `--allowedTools`/`--disallowedTools`. [Anthropic: CLI reference](https://docs.anthropic.com/en/docs/claude-code/cli-usage)

### OpenAI Codex

Codex dùng `AGENTS.md` cho chỉ dẫn theo repository/thư mục. Nên để task spec dài và repo-local instructions trong workspace file như `AGENTS.md` hoặc `repo/task.md`, sau đó prompt trỏ tới các file này thay vì nhồi toàn bộ vào một tin nhắn. [OpenAI: Custom instructions with AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md), [OpenAI: Sandbox agents](https://developers.openai.com/api/docs/guides/agents/sandboxes)

Cấu trúc thực tế có thể dùng:

```text
AGENTS.md                 # quy tắc chung và lệnh CI
docs/architecture.md      # boundary, dependency rule, ADR
docs/quality-gates.md     # test, lint, security gates
tasks/ISSUE-123.md        # context, acceptance criteria, constraints
```

Điểm cần nói trong phỏng vấn: context được **version-control**, review như code và cập nhật cùng kiến trúc; agent không được tự đoán invariant nghiệp vụ.

## 3. Quy trình thực thi trong SDLC

### Bước A — Triage và viết task có thể kiểm chứng

Trước khi gọi agent, chuyển issue thành:

1. mục tiêu và phạm vi;
2. acceptance criteria;
3. file/module được phép chạm;
4. invariant và rủi ro;
5. lệnh kiểm chứng và dữ liệu test;
6. điều kiện dừng hoặc cần hỏi người.

Đây là suy luận vận hành từ việc Claude Code/Codex nhận chỉ dẫn repo-local, chạy trong workspace, và có lớp phê duyệt cho side effects. [Anthropic: memory](https://docs.anthropic.com/en/docs/claude-code/memory), [OpenAI: AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md), [OpenAI: guardrails and human review](https://developers.openai.com/api/docs/guides/agents/guardrails-approvals)

Ví dụ task cho flash sale:

```text
Mục tiêu: thêm reservation API.
Acceptance: không oversell dưới concurrent requests; retry cùng idempotency key trả cùng kết quả.
Không đổi: payment contract và public response schema.
Kiểm chứng: unit, integration với transaction/locking, test 100 concurrent requests.
Dừng và hỏi: nếu cần đổi isolation level hoặc migration phá backward compatibility.
```

### Bước B — Plan trước khi edit

Yêu cầu agent:

- đọc tree và tài liệu liên quan;
- xác định entry points, data flow và test hiện hữu;
- nêu phương án, trade-off, file dự kiến sửa;
- chỉ bắt đầu sửa sau khi developer duyệt plan.

Claude Code có permission mode `plan`, cho phép phân tích mà không sửa file/chạy lệnh; CLI cũng có `--permission-mode plan`. [Anthropic: CLI reference](https://docs.anthropic.com/en/docs/claude-code/cli-usage), [Anthropic: IAM and permissions](https://docs.anthropic.com/en/docs/claude-code/iam)

Trong Codex, dùng task prompt và môi trường có workspace file để ép agent xuất plan trước khi thực thi; với hành động nhạy cảm, thiết kế workflow để run bị pause và chờ human review. [OpenAI: guardrails and human review](https://developers.openai.com/api/docs/guides/agents/guardrails-approvals)

### Bước C — Thực thi trong phạm vi nhỏ

Chia feature thành các lát cắt có thể review: schema/migration → domain logic → adapter/API → tests → docs. Mỗi lát cắt nên có một diff nhỏ, một nhóm test và một checkpoint.

Claude Code có `--allowedTools` và `--disallowedTools`; permission rules có thể đưa vào settings được version-control để nhóm dùng thống nhất. Quyền deny có ưu tiên cao hơn allow. [Anthropic: CLI reference](https://docs.anthropic.com/en/docs/claude-code/cli-usage), [Anthropic: IAM and permissions](https://docs.anthropic.com/en/docs/claude-code/iam)

Codex cung cấp các mode/environment và git worktree để cô lập các nhánh công việc. Worktree phù hợp khi cần nhiều agent làm song song mà không ghi đè checkout chính. [OpenAI: Codex environments](https://learn.chatgpt.com/docs/environments/modes), [OpenAI: Worktrees](https://learn.chatgpt.com/docs/environments/git-worktrees)

### Bước D — Verification loop

Agent phải chạy lệnh kiểm chứng, còn developer kiểm tra bằng chứng:

```text
format → lint/typecheck → unit tests → integration/contract tests
→ security/static scan → diff review → CI
```

Không coi câu “tests pass” là bằng chứng nếu không có lệnh, phạm vi test và output. Đối với concurrency/payment/auth/migration, thêm test cạnh tranh, rollback, idempotency, privilege boundary và backward compatibility.

Claude Code có workflow chính thức cho test/debug và chạy non-interactive trong CI; GitHub Action có thể kích hoạt review trên pull request hoặc comment. [Anthropic: common tasks](https://docs.anthropic.com/en/docs/claude-code/common-tasks), [Anthropic: GitHub Actions](https://docs.anthropic.com/en/docs/claude-code/github-actions)

Codex có luồng code review riêng để xem thay đổi và phát hiện vấn đề trước merge; với ứng dụng tự xây bằng Agents SDK, guardrails/tool guardrails và human review phải được khai báo trong harness vì không tự động thừa hưởng mọi review của Codex app. [OpenAI: Code review](https://learn.chatgpt.com/docs/code-review), [OpenAI: Guardrails and human review](https://developers.openai.com/api/docs/guides/agents/guardrails-approvals)

### Bước E — PR, merge và deploy

Agent có thể tạo summary, test evidence, migration notes và rollback plan. Merge/deploy vẫn đi qua CI, code owner và quy trình production của tổ chức. Với side effect như shell command, sửa hệ thống, gọi API nhạy cảm hoặc deploy, dùng approval gate; OpenAI mô tả run sẽ trả về interruption/state để ứng dụng phê duyệt hoặc từ chối rồi resume cùng run. [OpenAI: Guardrails and human review](https://developers.openai.com/api/docs/guides/agents/guardrails-approvals)

## 4. MCP: mở rộng context và hành động có kiểm soát

MCP là giao thức mở để kết nối model với nguồn dữ liệu và tool chuẩn hóa. Anthropic mô tả MCP như một lớp kết nối cho database, API và công cụ bên ngoài; Claude Code có thể cấu hình server bằng `claude mcp`. [Anthropic: Model Context Protocol](https://docs.anthropic.com/en/docs/agents-and-tools/mcp), [Anthropic: Connect Claude Code to tools via MCP](https://docs.anthropic.com/en/docs/claude-code/mcp)

Khi dùng MCP trong công việc, nói rõ:

- **Read-only trước**: issue tracker, docs, logs, schema.
- **Write tools có scope**: chỉ repo/branch/workspace được phép.
- **Approval ở biên side effect**: tạo PR, chỉnh ticket, gọi deploy.
- **Allowlist server**: chỉ dùng server do tổ chức tin cậy; kiểm tra auth, logging và data egress.

Anthropic khuyến nghị tự viết hoặc dùng MCP server của nhà cung cấp tin cậy và nhắc rằng họ không quản lý/audit các server MCP bên ngoài. [Anthropic: Claude Code security](https://docs.anthropic.com/en/docs/claude-code/security)

Codex có thể kết nối MCP trong CLI/IDE; ví dụ Docs MCP của OpenAI là read-only và cấu hình bằng `codex mcp add ...`. [OpenAI: Docs MCP](https://developers.openai.com/learn/docs-mcp), [OpenAI: Agents API overview](https://developers.openai.com/api/docs/guides/agents-api/overview)

## 5. Skills, hooks và subagents

### Skills

Skill là thư mục có `SKILL.md` mô tả workflow lặp lại và file hỗ trợ. OpenAI khuyên review skill và supporting files trước khi đưa vào agent vì skill là instruction có thể thay đổi cách agent hành động. [OpenAI: Skills](https://developers.openai.com/api/docs/guides/tools-skills), [OpenAI: Build skills for Codex](https://learn.chatgpt.com/docs/build-skills)

Cách áp dụng tốt:

- skill `run-tests`: biết lệnh test theo module và format output;
- skill `review-pr`: checklist invariants, security, migration;
- skill `incident-triage`: đọc logs, dựng hypothesis, tạo reproduction;
- mỗi skill có input, output, stop condition và lệnh được phép.

Skills giúp chuẩn hóa quy trình; chúng không thay thế code review hoặc policy.

### Subagents

Subagent phù hợp cho công việc độc lập, chạy song song hoặc cần context cô lập; không nên dùng cho task đơn giản, chỉnh một file hoặc chuỗi thao tác cần giữ cùng context. Đây là hướng dẫn trực tiếp trong tài liệu prompt/subagent của Anthropic và Codex hỗ trợ cấu hình subagents riêng. [Anthropic: Prompting best practices — subagent orchestration](https://docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/prompt-templates-and-variables), [OpenAI: Subagents](https://learn.chatgpt.com/docs/agent-configuration/subagents)

Mẫu phân rã:

```text
Lead agent: chốt contract và tích hợp
├── Explorer: tìm code path, rủi ro, test hiện hữu
├── Implementer: sửa domain/service
├── Test agent: bổ sung test cạnh tranh và regression
└── Reviewer: chỉ đọc diff, tìm violation/invariant
```

Lead agent phải kiểm tra artifact đầu ra (file/test/report) trước khi chuyển bước. Chạy song song chỉ khi các nhánh không tranh chấp file hoặc trạng thái.

### Hooks

Codex có hooks để chạy hành động tại các điểm trong lifecycle của agent; dùng hook cho format, kiểm tra policy, thu thập audit hoặc chặn lệnh nguy hiểm. [OpenAI: Hooks](https://learn.chatgpt.com/docs/hooks)

Claude Code có cơ chế permission/allowed tools và CLI/SDK để tự động hóa; khi chạy unattended, giới hạn rõ tool, số turn và output format. [Anthropic: CLI reference](https://docs.anthropic.com/en/docs/claude-code/cli-usage), [Anthropic: Claude Code SDK](https://docs.anthropic.com/en/docs/claude-code/sdk)

## 6. Sandbox, quyền và bảo mật

Claude Code mặc định yêu cầu phê duyệt cho thao tác có side effect như edit hoặc Bash; docs cũng nêu write access bị giới hạn trong thư mục làm việc và khuyên audit permission, dùng devcontainer cho code nhạy cảm. [Anthropic: Claude Code security](https://docs.anthropic.com/en/docs/claude-code/security), [Anthropic: IAM and permissions](https://docs.anthropic.com/en/docs/claude-code/iam)

`--dangerously-skip-permissions` bỏ qua prompt quyền và tài liệu CLI đánh dấu phải dùng thận trọng. Trong phỏng vấn, nên nói chỉ dùng trong sandbox dùng một lần với dữ liệu không nhạy cảm, không dùng cho checkout có credential hoặc production. [Anthropic: CLI reference](https://docs.anthropic.com/en/docs/claude-code/cli-usage)

OpenAI cảnh báo code do agent tạo có thể đọc file, credential và network mà environment cấp; nên cô lập workload, giới hạn outbound hosts, tách application key khỏi executor/environment key, không nhúng key vào source/image/logs và xoay vòng key khi nghi ngờ lộ. [OpenAI: Sandbox security](https://developers.openai.com/api/docs/guides/agents-api/environments/security), [OpenAI: Self-hosted sandboxes](https://developers.openai.com/api/docs/guides/agents-api/environments/self-hosted)

Một nguyên tắc phỏng vấn tốt là **least privilege + reversible changes + human approval ở ranh giới side effect**. Đây là kết luận vận hành rút ra từ permission systems, sandbox isolation và approval interruptions của hai bộ tài liệu trên. [Anthropic: security](https://docs.anthropic.com/en/docs/claude-code/security), [OpenAI: guardrails](https://developers.openai.com/api/docs/guides/agents/guardrails-approvals)

## 7. Enterprise setup và quản trị

Claude Code có thể chạy qua Anthropic API, Amazon Bedrock hoặc Google Vertex AI; tài liệu IAM mô tả role/SSO và permission rules có thể check-in vào version control. LLM gateway có thể tập trung authentication, usage tracking, budget/rate limit, audit log và model routing. [Anthropic: IAM](https://docs.anthropic.com/en/docs/claude-code/iam), [Anthropic: LLM gateway](https://docs.anthropic.com/en/docs/claude-code/llm-gateway)

OpenAI Agents API quản lý session/orchestration/context compaction và cho phép chọn OpenAI-hosted hoặc self-hosted environment. Khi self-hosted, executor kết nối outbound và dùng restricted environment key; application API key nên nằm ngoài sandbox. [OpenAI: Agents API overview](https://developers.openai.com/api/docs/guides/agents-api/overview), [OpenAI: Self-hosted sandboxes](https://developers.openai.com/api/docs/guides/agents-api/environments/self-hosted)

Một rollout an toàn nên có:

- pilot trên repo không chứa production secret;
- standard `CLAUDE.md`/`AGENTS.md`, skills và permission profiles;
- allowlist MCP server và tool;
- audit prompt/tool/action, cost và failure;
- CI bắt buộc, code owner review;
- kill switch, revoke key và rollback.

Các mục trên là thiết kế áp dụng (inference) dựa trên cơ chế access control, gateway, sandbox security và human review trong tài liệu chính thức; cần điều chỉnh theo chính sách công ty. [Anthropic: LLM gateway](https://docs.anthropic.com/en/docs/claude-code/llm-gateway), [Anthropic: security](https://docs.anthropic.com/en/docs/claude-code/security), [OpenAI: sandbox security](https://developers.openai.com/api/docs/guides/agents-api/environments/security)

## 8. Đo năng suất để tránh “AI nhanh nhưng rework nhiều”

Không nên chỉ đo số dòng code hoặc số task đóng. Dùng baseline trước/sau theo cùng loại task:

- lead time từ issue đến PR;
- thời gian từ PR đến review/merge;
- số vòng review và số lần agent phải sửa;
- thời gian viết test và tỷ lệ test pass;
- escaped defects, rollback và security findings;
- chi phí token/model trên mỗi PR;
- tỷ lệ task cần human takeover;
- mức độ sử dụng skill/MCP và lỗi do tool.

Anthropic CLI có output JSON và SDK result chứa session, số turn, duration và total cost; OpenAI Agents có session/events, tracing và observability/usage để thu thập dữ liệu vận hành. [Anthropic: CLI reference](https://docs.anthropic.com/en/docs/claude-code/cli-usage), [Anthropic: Claude Code SDK](https://docs.anthropic.com/en/docs/claude-code/sdk), [OpenAI: Agents API overview](https://developers.openai.com/api/docs/guides/agents-api/overview), [OpenAI: Agents API architecture](https://developers.openai.com/api/docs/guides/agents-api/architecture)

Cách trả lời gây ấn tượng:

> “Tôi đo cycle time và defect escape theo nhóm task trước khi rollout. Nếu PR nhanh hơn nhưng số vòng review, lỗi production hoặc chi phí tăng, tôi coi đó là thất bại. Tôi tối ưu context, permission và task decomposition trước khi đổi model.”

## 9. Bài luyện trả lời phỏng vấn

### Câu 1 — Bạn setup agent trong repo mới thế nào?

Khung trả lời:

> “Tôi tạo `CLAUDE.md`/`AGENTS.md` chứa architecture, commands, coding standards, invariants và quality gates; đưa vào git để cả nhóm review. Mỗi issue có acceptance criteria và task file. Agent chạy plan/read-only trước, sau đó làm trên feature branch hoặc worktree với allowlist tool. Mỗi thay đổi phải qua format, lint/typecheck, test và diff review. Permission/MCP chỉ cấp least privilege; production action có human approval.”

Nguồn: [Anthropic memory](https://docs.anthropic.com/en/docs/claude-code/memory), [OpenAI AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md), [OpenAI worktrees](https://learn.chatgpt.com/docs/environments/git-worktrees), [Anthropic IAM](https://docs.anthropic.com/en/docs/claude-code/iam).

### Câu 2 — Claude Code và Codex khác nhau ở đâu trong quy trình?

Khung trả lời:

> “Tôi không chọn theo thương hiệu mà theo harness và môi trường. Claude Code nổi bật ở CLI/SDK, `CLAUDE.md`, permission rules, MCP và workflow GitHub Actions. Codex có `AGENTS.md`, CLI/IDE/cloud, worktree, skills/hooks, code review và các mode/sandbox của Codex; Agents API còn tách harness và execution environment. Với cả hai, nguyên tắc chung vẫn là context chuẩn hóa, task nhỏ, verification tự động và human review cho side effect.”

Nguồn: [Anthropic CLI](https://docs.anthropic.com/en/docs/claude-code/cli-usage), [Anthropic GitHub Actions](https://docs.anthropic.com/en/docs/claude-code/github-actions), [OpenAI Codex CLI](https://developers.openai.com/codex/cli/), [OpenAI code review](https://learn.chatgpt.com/docs/code-review), [OpenAI Agents API](https://developers.openai.com/api/docs/guides/agents-api/overview).

### Câu 3 — Làm sao tránh agent sửa sai hoặc làm lộ secret?

Khung trả lời:

> “Tôi giới hạn workspace, file mount, network và tool; tách read-only khỏi write tools; không đưa production secret vào context; dùng environment key hạn quyền và secret manager/proxy; yêu cầu approval trước deploy, migration hoặc API side effect; kiểm tra diff và CI. Nếu chạy unattended, tôi dùng sandbox dùng một lần, `max-turns`, allowlist và audit log.”

Nguồn: [Anthropic security](https://docs.anthropic.com/en/docs/claude-code/security), [Anthropic CLI](https://docs.anthropic.com/en/docs/claude-code/cli-usage), [OpenAI sandbox security](https://developers.openai.com/api/docs/guides/agents-api/environments/security), [OpenAI approvals](https://developers.openai.com/api/docs/guides/agents/guardrails-approvals).

### Câu 4 — Khi nào dùng subagent?

Khung trả lời:

> “Tôi dùng khi các luồng độc lập có thể chạy song song hoặc cần context cô lập, chẳng hạn explorer, test và security review. Tôi không spawn cho chỉnh một file hay chuỗi thao tác phụ thuộc trạng thái. Lead agent phải kiểm tra artifact và hợp nhất bằng diff/CI.”

Nguồn: [Anthropic subagent orchestration](https://docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/prompt-templates-and-variables), [OpenAI subagents](https://learn.chatgpt.com/docs/agent-configuration/subagents).

### Câu 5 — Làm sao chứng minh năng suất tăng?

Khung trả lời:

> “Tôi so sánh baseline theo loại task: lead time, review rounds, test authoring time, escaped defects, rollback, takeover rate và cost/PR. Tôi giữ quality gates bất biến; chỉ kết luận tăng năng suất khi tốc độ tăng mà chất lượng và chi phí vẫn trong ngưỡng.”

Nguồn về số liệu phiên/chi phí: [Anthropic CLI](https://docs.anthropic.com/en/docs/claude-code/cli-usage), [Anthropic SDK](https://docs.anthropic.com/en/docs/claude-code/sdk), [OpenAI Agents API](https://developers.openai.com/api/docs/guides/agents-api/overview).

## 10. Checklist trước khi nhận mình “đã áp dụng AI agent”

- [ ] Có một repo thật và một feature có acceptance criteria.
- [ ] Có `CLAUDE.md` hoặc `AGENTS.md` ngắn, cụ thể, version-control.
- [ ] Có plan/read-only phase và branch/worktree cô lập.
- [ ] Có allowlist/denylist tool và policy cho MCP.
- [ ] Có test evidence, diff review và CI.
- [ ] Có ví dụ agent tìm ra bug hoặc giúp viết regression test.
- [ ] Có số liệu trước/sau và ghi nhận failure mode.
- [ ] Có câu trả lời rõ ràng về secret, approval, rollback và ownership.

Câu kết nên luyện:

> “Agent đảm nhiệm phần đọc code, thực thi lặp lại và rút ngắn feedback loop. Tôi chịu trách nhiệm về yêu cầu, boundary kiến trúc, invariant nghiệp vụ, bảo mật, bằng chứng kiểm thử và quyết định merge/deploy.”
