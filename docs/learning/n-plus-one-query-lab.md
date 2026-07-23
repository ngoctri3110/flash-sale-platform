# N+1 Query Lab

## Controlled scenario

`NPlusOneQueryLabIntegrationTest` inserts four orders that refer to four distinct
products, then loads a test-only JPA mapping of the existing `orders` table. It
uses Hibernate `Statistics#getPrepareStatementCount`, not generated SQL text.
This proves database work without becoming coupled to a Hibernate SQL formatting
detail.

Run the lab from `backend`:

```powershell
.\mvnw.cmd -Dtest=NPlusOneQueryLabIntegrationTest test
```

The lazy scenario deliberately executes more than two statements: one for the
orders and one per distinct lazy product. The other scenarios have bounded
statement budgets. The fixture clears the persistence context and statistics
before every observation, so first-level-cache hits cannot hide the problem.

## Equivalent strategies

| Strategy | Statements | Pagination | Risk / use it when |
| --- | --- | --- | --- |
| Lazy loop (N+1) | 1 + N | works, slow | Demonstration only. |
| `join fetch` | 1 in this to-one lab | Do not use a to-many fetch join with a page | A bounded detail graph. |
| `EntityGraph` | 1 in this to-one lab | Same collection caveat | A readable, repository-specific graph. |
| `@BatchSize(4)` | At most 2 for four products | works | Existing lazy model with several to-one associations. |
| DTO projection | 1 (plus count for a page) | works | Production lists: select only response columns. |

The first three alternatives solve different problems. A fetch join puts the
graph beside the query; an entity graph keeps the query text shorter; batching
reduces, rather than removes, deferred loads. DTO projection has the least data
and least entity-graph coupling for a list response, but it is intentionally
read-specific and cannot be reused for an update aggregate.

## Production choice

Product and Order list paths use query-specific projections:
`ProductJpaRepository#findProductList` selects a `ProductListProjection`, while
`JdbcOrderQuery` selects its response snapshot directly. The lab includes a
regression guard: the production product page must stay within two prepared
statements (content plus count). This avoids entity graph coupling and the
Cartesian-product risk of collection fetch joins.

Use a fetch join or entity graph for a detail screen only when its bounded graph
is known. Batch fetching is a safe fallback, not a replacement for a list DTO.
Global `EAGER` is not the default fix: it moves hidden work to unrelated loads
and can still create N+1 across query boundaries.

## Regression guard

For a list endpoint, assert a bounded statement count through Hibernate
statistics or a datasource proxy, rather than comparing SQL text. The bound
must include the page content query and its count query, and fails if a future
mapper dereferences lazy associations per row.
