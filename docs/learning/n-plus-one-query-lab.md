# N+1 Query Lab

## Controlled scenario

Load 20 `OrderEntity` records and then read each lazy `product` association. The
initial select plus one product select per distinct product is N+1. Enable
`org.hibernate.SQL=DEBUG` only in a lab profile and count JDBC statements; do
not assert generated SQL strings in regression tests.

## Equivalent strategies

| Strategy | Statements | Pagination | Risk / use it when |
| --- | --- | --- | --- |
| Lazy loop (N+1) | 1 + N | works, slow | Demonstration only. |
| `join fetch` | usually 1 | unsafe for to-many joins | A bounded detail graph; avoid collection fetch joins with pages. |
| `@EntityGraph` | usually 1 | same collection caveat | Repository method needs a named, readable graph. |
| `default_batch_fetch_size` | 1 + ceil(N/batch) | works | Existing lazy model, several to-one associations. |
| DTO projection | 1 + count | works | Production lists: select only response columns. |

## Production choice

Product and Order list paths already use query-specific projections:
`JpaProductRepositoryAdapter` maps the Product page, while `JdbcOrderQuery`
selects its response snapshot directly. This avoids entity graph coupling and
the Cartesian-product risk of collection fetch joins.

Use a fetch join/entity graph for a detail screen only when its bounded graph is
known. Batch fetching is a safe fallback, not a replacement for a list DTO.
Global `EAGER` is not the default fix: it moves hidden work to every load and
can still produce N+1 across query boundaries.

## Regression guard

For a list endpoint, assert a bounded statement count through Hibernate
statistics or a datasource proxy, rather than comparing SQL text. The bound
must include the page content query and its count query, and should fail if a
future mapper dereferences lazy associations per row.
