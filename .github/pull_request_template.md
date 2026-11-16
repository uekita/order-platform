## What changed

<!-- Describe the behaviour change and the risk it carries. -->

## Test evidence

- [ ] `./scripts/local-stack-up.sh` succeeds
- [ ] `mvn -f qa/backend-test-automation/pom.xml test -Plocal`
- [ ] Relevant tag preset run and Allure artifact reviewed
- [ ] New async assertions use Awaitility, not `Thread.sleep`
- [ ] New test data is unique and safe for parallel/shared environments
- [ ] Secrets are referenced through environment variables / GitHub environments only
