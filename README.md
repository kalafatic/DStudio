# DStudio
### DARWIN STUDIO — AI Coding Platform

DStudio is an independent, extensible AI coding platform designed to coordinate and integrate multiple coding agents, development tools, LLM providers, and software engineering workflows.

Its primary purpose is to provide a unified development environment in which users can delegate, monitor, review, and manage complex software development tasks.

**DStudio is a standalone product.** It must operate independently of EVO, TRYST, and any other external application.

### Core Responsibilities

- Provide a professional development environment and user interface.
- Manage development tasks and coordinate coding agents.
- Integrate multiple coding agents through well-defined interfaces.
- Provide Darwin Coder as its native and default coding agent.
- Support optional external agents such as OpenCode, Aider, and other compatible implementations.
- Manage agent configurations and LLM provider connections.
- Coordinate access to shared resources without taking ownership of unrelated external services.
- Provide task monitoring, progress reporting, results, diffs, and execution history.
- Support independent and parallel development workflows.
- Provide native TEIP integration for communication with external platforms such as EVO and TRYST.

### Architecture Principles

- Java 21.
- Eclipse RCP / Equinox / OSGi.
- Maven / Tycho.
- Separation of application core, services, integration interfaces, and UI.
- Headless server operation and standalone RCP client.
- No dependency on Spring, Node.js, Python, or external message brokers.
- Technology-independent architecture with extensible tool and language adapters.
- Explicit API and protocol versioning.
- Robust lifecycle management, concurrency control, observability, and error recovery.
- No duplicated task or agent orchestration.

### Integration

DStudio communicates with Darwin Coder and other coding agents through defined interfaces.

External platforms such as EVO and TRYST communicate with DStudio through TEIP. They submit development requests and receive progress, results, and status information without depending on DStudio's internal implementation.

### Architectural Boundary

DStudio owns platform-level task coordination, agent integration, user interaction, and platform services.

It does not implement the internal intelligence or coding workflow of every integrated agent. Those responsibilities belong to the respective coding agents.

### Development Strategy

Build a robust, extensible architecture from the beginning. Initial implementation should focus on the technologies required by EVO, TRYST, and the DStudio platform itself, while keeping the architecture open to other languages, tools, and development environments.

**The long-term goal is a universal, extensible AI coding platform.**
