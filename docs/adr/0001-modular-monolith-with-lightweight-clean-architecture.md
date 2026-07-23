# Use a modular monolith with lightweight Clean Architecture

The MVP will be one Spring Boot deployable organized package-by-feature around product, inventory, order, and messaging. Dependencies point from API and infrastructure toward application/domain code, but ports, separate persistence models, and mappers are introduced only where a real boundary or testability need justifies their cost; microservices and ceremony-only abstractions are deliberately deferred.
