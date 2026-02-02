<!--
  Sync Impact Report
  ==================
  Version change: 0.0.0 → 1.0.0 (initial ratification)

  Added Principles:
  - I. Code Quality
  - II. Test-Driven Development (TDD)
  - III. Behavior-Driven Development (BDD)
  - IV. Domain-Driven Design (DDD)
  - V. SOLID Principles
  - VI. Hexagonal Architecture
  - VII. Layered Architecture & Dependency Inversion

  Added Sections:
  - Layer Definitions
  - Data Transfer & Mapping
  - Governance

  Removed Sections: None (initial creation)

  Templates Requiring Updates:
  - .specify/templates/plan-template.md: ⚠️ pending (add Constitution Check gates)
  - .specify/templates/spec-template.md: ✅ no changes required
  - .specify/templates/tasks-template.md: ⚠️ pending (add layer-specific task patterns)

  Follow-up TODOs: None
-->

# Resilience4j PoC Constitution

## Core Principles

### I. Code Quality

All code MUST adhere to high-quality standards that ensure maintainability, readability, and reliability.

**Non-Negotiable Rules**:
- Code MUST be self-documenting with clear naming conventions (classes, methods, variables)
- Methods MUST follow single responsibility—one method does one thing
- Cyclomatic complexity per method MUST NOT exceed 10
- Code duplication MUST be eliminated through appropriate abstraction
- All public APIs MUST have clear Javadoc documentation
- Magic numbers and strings MUST be extracted to named constants
- Null checks MUST use Optional or explicit null-handling patterns
- All warnings from static analysis tools (SpotBugs, Checkstyle) MUST be resolved

**Rationale**: High code quality reduces bugs, improves maintainability, and enables efficient onboarding of new team members.

### II. Test-Driven Development (TDD)

All feature development MUST follow the Red-Green-Refactor cycle. Tests are written BEFORE implementation code.

**Non-Negotiable Rules**:
- NO production code is written without a failing test first
- Tests MUST be written in this order: (1) Write failing test → (2) Implement minimal code to pass → (3) Refactor while keeping tests green
- Test coverage for domain and application layers MUST be ≥ 80%
- Each test MUST test exactly one behavior (single assertion principle where practical)
- Test names MUST follow `should_[expectedBehavior]_when_[condition]` pattern
- Tests MUST be independent—no shared mutable state between tests
- Tests MUST be deterministic—same input always produces same result

**Rationale**: TDD ensures all code is testable by design, reduces defect rates, and provides living documentation of system behavior.

### III. Behavior-Driven Development (BDD)

Integration and acceptance tests MUST use BDD-style specifications that express business requirements in domain language.

**Non-Negotiable Rules**:
- Acceptance tests MUST use Given-When-Then format
- Test scenarios MUST be written from the user/business perspective, not technical implementation
- Each user story from PRD MUST have corresponding BDD acceptance tests
- BDD scenarios MUST be readable by non-technical stakeholders
- Edge cases and error scenarios MUST be explicitly specified as BDD scenarios

**Example Format**:
```gherkin
Given 支付閘道連續失敗達到 60% 閾值
When 新的支付請求進入系統
Then 斷路器狀態變為 OPEN
And 請求在 50ms 內返回快速失敗回應
And 返回訊息為「支付服務暫時不可用，請嘗試其他支付方式或稍後重試」
```

**Rationale**: BDD bridges the gap between business requirements and technical implementation, ensuring the system delivers actual business value.

### IV. Domain-Driven Design (DDD)

The system MUST be modeled around the business domain with clear separation between domain logic and technical infrastructure.

**Non-Negotiable Rules**:
- Domain model MUST reside in the `domain` layer with zero external dependencies
- Domain entities MUST encapsulate business rules and invariants
- Value Objects MUST be used for concepts with no identity (e.g., Money, OrderId)
- Domain services MUST be used for operations that don't naturally belong to a single entity
- Ubiquitous Language from PRD MUST be reflected in code (訂單→Order, 庫存→Inventory, 支付→Payment, 物流→Shipping)
- Aggregates MUST define transactional boundaries
- Repository interfaces MUST be defined in domain layer, implementations in infrastructure

**Rationale**: DDD ensures the codebase directly reflects business concepts, making it easier to understand, modify, and communicate with stakeholders.

### V. SOLID Principles

All code MUST adhere to SOLID principles for object-oriented design.

**Non-Negotiable Rules**:

- **Single Responsibility Principle (SRP)**: Each class MUST have exactly one reason to change. A class MUST NOT mix concerns (e.g., no business logic in controllers, no persistence in domain).

- **Open/Closed Principle (OCP)**: Classes MUST be open for extension but closed for modification. New behavior SHOULD be added through new implementations, not by modifying existing code.

- **Liskov Substitution Principle (LSP)**: Subtypes MUST be substitutable for their base types without altering correctness. Implementations of interfaces MUST honor the contract.

- **Interface Segregation Principle (ISP)**: Clients MUST NOT be forced to depend on interfaces they don't use. Large interfaces MUST be split into smaller, role-specific interfaces.

- **Dependency Inversion Principle (DIP)**: High-level modules MUST NOT depend on low-level modules. Both MUST depend on abstractions. Inner layers define interfaces, outer layers provide implementations.

**Rationale**: SOLID principles create flexible, maintainable, and testable code that can evolve with changing requirements.

### VI. Hexagonal Architecture

The system MUST follow Hexagonal (Ports and Adapters) Architecture with clear layer boundaries.

**Non-Negotiable Rules**:
- The system MUST be organized into three concentric layers: Domain (innermost), Application (middle), Infrastructure (outermost)
- Domain layer MUST contain: entities, value objects, domain services, repository interfaces (ports)
- Application layer MUST contain: use cases, application services, port interfaces for external systems
- Infrastructure layer MUST contain: all framework dependencies, adapters, configurations, external service implementations
- Frameworks (Spring, Resilience4j, etc.) MUST only appear in Infrastructure layer
- Annotations from frameworks (`@Component`, `@Service`, `@Retry`, etc.) MUST only appear in Infrastructure layer
- Domain and Application layers MUST be framework-agnostic and independently testable

**Package Structure MUST Follow**:
```
com.example.order
├── domain/           ← Innermost: pure business logic, no dependencies
│   ├── model/        ← Entities, Value Objects, Aggregates
│   ├── service/      ← Domain Services
│   └── port/         ← Outbound Port interfaces (Repository, Gateway)
├── application/      ← Middle: orchestration, depends only on domain
│   ├── port/
│   │   ├── in/       ← Inbound Port interfaces (Use Cases)
│   │   └── out/      ← Outbound Port interfaces (if not in domain)
│   └── service/      ← Application Services implementing Use Cases
└── infrastructure/   ← Outermost: frameworks, adapters, configs
    ├── adapter/
    │   ├── in/web/   ← Inbound Adapters (Controllers, REST endpoints)
    │   └── out/      ← Outbound Adapters (Repository impl, External APIs)
    ├── config/       ← Framework configurations
    └── exception/    ← Technical exception handling
```

**Rationale**: Hexagonal Architecture isolates business logic from technical concerns, making the core domain testable without infrastructure and enabling technology changes without affecting business rules.

### VII. Layered Architecture & Dependency Inversion

Dependencies MUST flow inward. Inner layers MUST NOT know about outer layers.

**Non-Negotiable Rules**:

**Dependency Direction**:
- Infrastructure → Application → Domain (dependencies flow inward)
- Domain layer MUST have ZERO dependencies on other layers
- Application layer MAY depend on Domain layer only
- Infrastructure layer MAY depend on Application AND Domain layers

**Cross-Layer Access**:
- Infrastructure layer MAY directly use Application and Domain layer classes
- Application and Domain layers MUST access Infrastructure ONLY through interfaces (ports)
- Inbound adapters (controllers) MUST call Application services through Use Case interfaces
- Outbound adapters MUST implement Port interfaces defined in Application/Domain layers

**Dependency Injection**:
- All inter-layer dependencies MUST be injected, not instantiated
- Constructor injection MUST be preferred over field injection
- Interfaces MUST be used for all injected dependencies crossing layer boundaries

**Rationale**: Strict dependency inversion ensures the core business logic remains stable and testable while allowing flexibility in infrastructure choices.

## Layer Definitions

### Domain Layer (Innermost)

**Purpose**: Contains pure business logic and rules independent of any framework or external system.

**Contains**:
- Entities with business behavior (Order, OrderItem, OrderStatus)
- Value Objects (OrderId, Money, SkuCode)
- Domain Services (business operations spanning multiple entities)
- Port Interfaces (outbound) for repository and gateway abstractions

**Rules**:
- MUST NOT import any framework classes
- MUST NOT have any I/O operations
- MUST be 100% unit-testable with no mocks for external systems

### Application Layer (Middle)

**Purpose**: Orchestrates use cases by coordinating domain objects and infrastructure services.

**Contains**:
- Use Case interfaces (inbound ports)
- Application Services (use case implementations)
- DTOs for use case input/output (if needed)
- Port Interfaces (outbound) for external service abstractions

**Rules**:
- MUST NOT contain business rules (delegate to Domain)
- MUST NOT contain framework-specific code
- MAY define transactional boundaries
- MUST use Domain layer interfaces for persistence

### Infrastructure Layer (Outermost)

**Purpose**: Provides implementations for all external concerns—frameworks, databases, external APIs.

**Contains**:
- Inbound Adapters: REST controllers, CLI handlers, event listeners
- Outbound Adapters: Repository implementations, external API clients, message publishers
- Framework Configurations: Spring configs, Resilience4j configs, WebClient configs
- Technical Exception Handlers: GlobalExceptionHandler

**Rules**:
- MUST implement interfaces defined in inner layers
- MAY use any framework or library
- MUST handle framework-specific exceptions and translate to domain exceptions
- All Resilience4j decorators (`@Retry`, `@CircuitBreaker`, `@TimeLimiter`) MUST be applied here

## Data Transfer & Mapping

Data crossing layer boundaries MUST be transformed through explicit mappers to maintain layer isolation.

**Non-Negotiable Rules**:

- **Request DTOs**: Web layer (Infrastructure) MUST use its own DTOs for HTTP requests. These MUST be mapped to domain objects before entering Application layer.

- **Response DTOs**: Domain objects MUST be mapped to response DTOs before leaving Application layer to Infrastructure.

- **Mapper Classes**: Each layer boundary MUST have dedicated mapper classes:
  - `WebMapper`: HTTP DTOs ↔ Application DTOs / Domain Objects
  - `PersistenceMapper`: Domain Objects ↔ JPA Entities / Database Records
  - `ExternalServiceMapper`: Domain Objects ↔ External API DTOs

- **Mapper Location**: Mappers MUST reside in Infrastructure layer (they know both sides)

- **No Domain Leakage**: Domain objects MUST NOT appear in REST API contracts or be serialized directly to JSON

**Example**:
```
HTTP Request → CreateOrderRequest (web DTO)
             → OrderMapper.toDomain()
             → Order (domain entity)
             → process in Application/Domain
             → OrderMapper.toResponse()
             → CreateOrderResponse (web DTO)
             → HTTP Response
```

**Rationale**: Explicit mapping prevents tight coupling between layers and allows each layer to evolve independently without breaking contracts.

## Governance

### Amendment Process

1. Any team member MAY propose amendments to this constitution
2. Amendments MUST be documented with rationale and impact analysis
3. Amendments MUST be reviewed by at least one architect
4. Breaking changes (MAJOR version) MUST include migration plan
5. All amendments MUST be versioned and dated

### Versioning Policy

This constitution follows Semantic Versioning:
- **MAJOR**: Removal or redefinition of core principles (backward incompatible)
- **MINOR**: Addition of new principles or material expansion of existing ones
- **PATCH**: Clarifications, wording improvements, typo fixes

### Compliance Verification

- All PRs MUST include constitution compliance check in review
- Automated checks SHOULD verify layer dependency rules
- Architecture Decision Records (ADRs) MUST reference constitution principles
- Sprint retrospectives SHOULD review constitution adherence

### Conflict Resolution

- This constitution supersedes all other development practices
- When constitution conflicts with external requirements, escalate to architecture team
- Runtime development guidance in `CLAUDE.md` MUST align with these principles

**Version**: 1.0.0 | **Ratified**: 2026-02-02 | **Last Amended**: 2026-02-02
