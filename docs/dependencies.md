# Dependency decisions

Verified on 12 November 2026 against official documentation and published package metadata. Exact frontend direct versions are pinned in `frontend/package.json`; `package-lock.json` locks the full resolved tree. The backend parent BOM supplies compatible Spring, MongoDB driver, Jackson, Tomcat and Mockito versions.

## Current runtime

- Java **21**: project compilation target and primary backend language. Verified with Temurin 21.0.13+9. The optional PowerShell installer downloads the official Windows x64 archive and checks its pinned SHA-256. [Adoptium release](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.13%2B9)
- Spring Boot **4.1.2**: stable line with Java 21 support, Maven 3.6.3+ minimum; this project uses Maven 3.9.11. [System requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- Spring starters: `webmvc`, `data-mongodb`, `security`, `validation`, `actuator`; Spring Security OAuth2 resource-server and JOSE modules provide JWT verification/issuance (7.1.2 from the BOM, Nimbus JOSE 10.9.1 transitively). Boot manages their transitive versions. `spring.mongodb.uri` is the Boot 4 connection setting used here.
- springdoc-openapi **3.2.0**: the Spring Boot 4 compatible major, checked against its official compatibility matrix. [springdoc](https://springdoc.org/)
- MongoDB Community **8.0**: a dedicated native single-node replica set on port 27018 supports the booking transactions. The existing Windows service on port 27017 is preserved. [Windows installation](https://www.mongodb.com/docs/manual/tutorial/install-mongodb-on-windows/), [transactions](https://www.mongodb.com/docs/manual/core/transactions/)
- React/React DOM **19.3.1**, Vite **8.3.8**, React Vite plugin **6.3.0**, TypeScript **7.0.2**, Tailwind/Tailwind Vite **4.3.5**. The installed Node runtime is 22.19.0; use a supported LTS version satisfying Vite's 22.12+ line. [React versions](https://react.dev/versions), [Vite requirements](https://vite.dev/guide/), [Tailwind Vite integration](https://tailwindcss.com/docs/installation/using-vite)
- React Router **7.18.5**, Axios **1.21.1**, TanStack Query **5.107.1**, Zustand **5.1.0**, Lucide React **1.72.0**, Framer Motion **14.12.0**. Used for routes, API calls, server-state caching, browser preferences, icons, and reduced-motion-aware animation respectively.
- Recharts **3.10.1** renders the administrator's seven-day sales chart; `qrcode.react` **4.2.0** renders ticket QR codes. Both routes are lazy loaded.

## Tests and the JUnit compatibility decision

JUnit Jupiter **5.14.3** is pinned to preserve the requested JUnit 5 stack. Mockito is BOM-managed. **Do not combine Spring Framework 7's `SpringExtension` with JUnit 5**: the framework extension now requires JUnit 6. The initial integration attempt exposed this mismatch and was replaced, not suppressed. [Spring testing](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html)

The integration suite instead uses ordinary JUnit 5 lifecycle methods to start the actual Spring application on a random port, access its real MongoDB repositories and send Java HTTP client requests. This exercises the real security filter chain and serialization without Spring's JUnit extension. It owns and removes only a random `cinenext_test_*` database. Unit tests use plain Mockito. No embedded MongoDB substitute is used.

Frontend tests use Vitest **5.1.0**, Testing Library React **16.3.3**, user-event **14.6.10**, jest-dom **7.0.1**, and jsdom **26.1.0**. Component API fixtures are confined to tests; the running frontend has no mock fallback. jsdom 26.1.0 supports the installed Node 22.19 runtime; jsdom 31 required a newer patch.

## Dependencies introduced with later milestones

Refresh-session management; Spring WebSocket for groups; Spring Data Redis for caches/rate limits; Spring Kafka plus a MongoDB transactional outbox; Micrometer's Prometheus registry; Prometheus, Grafana and k6. Select and lock compatible stable versions when their modules are implemented, then test them against the current BOM.

Redis and Kafka will use WSL2/native-supported service setups or managed endpoints. Integration tests already use isolated real databases on the local replica set. No container dependency is introduced. Spring AI and React Three Fiber remain optional and are not required to build or run the booking MVP.

Keep the project on a tested dependency set. Re-run type checking, unit/integration tests and API smoke checks when upgrading, rather than installing a major version merely because a registry marks it latest.
