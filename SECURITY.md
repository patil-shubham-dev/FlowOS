# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in FlowOS, please report it responsibly.

**Do NOT open a public GitHub issue for security vulnerabilities.**

Instead, please email: **security@flowos.dev** (or create a private advisory on GitHub)

Include:
- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

## Response Timeline

- **Acknowledgment**: within 48 hours
- **Initial assessment**: within 1 week
- **Fix or mitigation**: depends on severity, but we aim for quick resolution

## Security Model

FlowOS is designed with security in mind:

### Data storage
- All data lives in a local SQLite database
- No data leaves your machine unless you configure a cloud AI provider
- API keys are stored in the database, not in environment variables or config files

### Agent safety
- Every agent action is logged to an audit trail
- Risky operations require user confirmation (confirm tier)
- Destructive operations are blocked by default (blocked tier)
- Every action is undoable via the audit log
- The agent is scoped to visible context only — it cannot see data you're not viewing

### Provider communication
- API keys are sent only to the configured AI provider
- No data is sent to third parties beyond the configured provider
- Provider connections can be validated before use

### Permissions
- Tool risk tiers can be overridden per-tool
- Users can raise friction (auto → confirm) freely
- Lowering friction requires staying within safe bounds
- Blocked tools can never go below confirm tier

## Scope

This security policy applies to the FlowOS application code in this repository. It does not apply to:

- Third-party AI providers (Anthropic, Nvidia, OpenAI, etc.)
- Your operating system or network security
- Dependencies (though we monitor for known vulnerabilities)

## Dependency Security

We use `npm audit` to monitor for known vulnerabilities in dependencies. If you discover a vulnerable dependency, please report it.
