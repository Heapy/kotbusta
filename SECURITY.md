# Security Policy

## Supported Versions

Kotbusta follows a rolling release model with the `main` branch as the primary release channel.

| Version/Tag          | Support Status        | Notes                                              |
|----------------------|-----------------------|----------------------------------------------------|
| `main` (latest)      | ✅ **Fully Supported** | Recommended for production use                     |
| Tagged releases      | ⚠️ Limited Support    | Security patches only for critical vulnerabilities |
| Development branches | ❌ Not Supported       | Use at your own risk                               |

### Docker Image Policy

- **Production Use**: Always use the `main` tag for the latest stable version
- **Updates**: It is safe and recommended to update to the latest `main` Docker tag
- **Security Patches**: Applied immediately to the `main` branch

## Reporting a Vulnerability

We take security seriously at Kotbusta. If you discover a security vulnerability, please follow these steps:

### 🔐 Report via GitHub Security Advisories (Preferred)

1. **Do not** create a public issue
2. Report vulnerabilities privately through [GitHub Security Advisories](https://github.com/Heapy/kotbusta/security/advisories)
3. Click "Report a vulnerability" button
4. Provide detailed information including:
   - Vulnerability description
   - Steps to reproduce
   - Potential impact
   - Suggested remediation (if any)

### Response Timeline

- **Initial Response**: Within 48 hours
- **Vulnerability Assessment**: Within 5 business days
- **Patch Development**: Based on severity:
  - Critical: Within 24-48 hours
  - High: Within 1 week
  - Medium: Within 2 weeks
  - Low: Next regular release

## Vulnerability Disclosure Process

1. **Private Disclosure**: Report through secure channels
2. **Verification**: We verify and assess the vulnerability
3. **Fix Development**: Create and test patches
4. **Coordinated Release**:
   - Apply fix to `main` branch
   - Update Docker images
   - Publish security advisory
5. **Public Disclosure**: After patches are available

## Security Features

Kotbusta includes several security features:

- **Kotlin Type Safety**: Leverages Kotlin's null-safety and type system
- **Minimal Dependencies**: Reduced attack surface
- **Container Isolation**: Designed for containerized deployments
- **Input Validation**: Comprehensive request validation
- **Secure Defaults**: Security-first configuration

## Recognition

We appreciate security researchers who help keep Kotbusta secure. Contributors who report valid security issues will be acknowledged in our security advisories (with permission).

## Resources

- [Security Advisories](https://github.com/Heapy/kotbusta/security/advisories)
- [Release Notes](https://github.com/Heapy/kotbusta/releases)
