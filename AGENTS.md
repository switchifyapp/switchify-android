# Switchify Development Guidelines for AI Agents

## Project Overview
Switchify is an Android accessibility service app that helps users with mobility impairments navigate their devices using various input methods including switches, camera gestures, and scanning techniques.

## Code Style & Standards

### Kotlin Conventions
- Follow existing code patterns and naming conventions
- Use existing libraries and utilities already in the codebase
- Check imports and dependencies - never assume libraries are available
- Look at neighboring files and existing components for patterns
- NO comments unless explicitly requested
- Use meaningful variable and function names that are self-documenting

### Architecture Patterns
- Follow MVVM architecture with ViewModels and Compose UI
- Use coroutines for async operations with appropriate dispatchers
- Implement proper error handling without exposing sensitive data
- Use dependency injection patterns already established

### Security Best Practices
- Never introduce code that exposes or logs secrets/keys
- Never commit secrets or keys to repository
- Follow defensive security practices only
- Remove unnecessary telemetry and external data collection

### Build Properties
- Leave properties files alone. Do not create or modify `local.properties`, `gradle.properties`, or any other properties files.
- Never add dummy keys or SDK paths. Builds must rely on the developer's local environment.
- If a build requires keys or SDK configuration, ask the user to set them locally; do not commit or push any properties changes.

## Development Workflow

### Required Workflow Process
**MANDATORY**: Follow this exact workflow for ALL development tasks:

1. **Milestone Creation**: Always create milestone `v*.*.*` format first
2. **Issue Creation**: Create GitHub issue and assign to milestone (with user consent)
3. **Branch Creation**: Create branch `feature/description-issue#` or `fix/description-issue#`
4. **Development**: Make changes following code standards
5. **Testing**: Test compilation with `./gradlew compileDebugKotlin`
6. **Commit & Push**: Commit with proper format and push branch
7. **Pull Request**: Create PR with detailed description
8. **User Approval**: Ask user if they want to merge (yes/no)
9. **Proactive Communication**: Always ask yes/no for perceived next steps

### Branch & Issue Management
- Create descriptive branch names: `feature/description-1234` or `fix/description-1234`
- Always create GitHub issues before starting work
- Assign issues to appropriate milestones WITH USER CONSENT
- Use GitHub CLI (`gh`) for all GitHub operations
- Always be proactive and ask yes/no for what you perceive to be the next step

### Commit Standards
Follow this exact format:
```
Brief descriptive title

- Bullet points describing changes
- Focus on what and why, not just what
- Include technical details relevant to reviewers

🤖 Auto-generated
```

### Pull Request Process
1. Create milestone v*.*.* format
2. Create GitHub issue and assign to milestone (with user consent)
3. Create feature branch from main
4. Make changes following code standards
5. Test compilation with `./gradlew compileDebugKotlin`
6. Commit with proper format
7. Push branch and create PR with detailed description
8. Include test plan in PR description
9. Ask user if they want to merge (yes/no)

### Testing Requirements
- Always test compilation before committing
- Run lint/typecheck commands if available (npm run lint, ruff, etc.)
- Never assume test frameworks - check README or search codebase
- Build must be successful before merging

## Tool Usage Patterns

### Preferred Tools
- Use `Bash` tool for git operations and builds
- Use `Grep` for searching code (never bash grep/rg)
- Use `Glob` for file pattern matching
- Use `Read` tool for examining files
- Use `Edit` or `MultiEdit` for code changes
- Use `TodoWrite` for task tracking on complex work

### TodoWrite Usage
Use TodoWrite tool for:
- Complex multi-step tasks (3+ steps)
- Non-trivial tasks requiring planning
- User-requested todo lists
- Multiple tasks provided by user
- Tracking progress through implementation

Do NOT use TodoWrite for:
- Single straightforward tasks
- Trivial tasks with <3 steps
- Purely conversational requests

### File Operations
- ALWAYS prefer editing existing files over creating new ones
- NEVER create documentation files unless explicitly requested
- Use absolute paths, not relative paths
- Read files before editing to understand context

## Android-Specific Guidelines

### UI Development
- Use Jetpack Compose following existing patterns
- Follow Material 3 design system
- Check existing components before creating new ones
- Use proper theming with MaterialTheme.colorScheme
- Handle external links with Intent.ACTION_VIEW and toUri()

### Accessibility Service Development
- Understand this is an accessibility service with special permissions
- Be careful with background processing and ANR prevention
- Use proper coroutine scoping and dispatchers
- Handle accessibility events efficiently
- Consider performance impact of tree processing

### Dependencies & Libraries
- Check package.json, build.gradle.kts, or cargo.toml before using libraries
- Use existing dependency patterns
- Don't add new dependencies without understanding existing ones
- Check if functionality already exists in codebase

## Performance Considerations

### Background Processing
- Use appropriate coroutine dispatchers (Default for CPU work, IO for network)
- Implement timeout mechanisms for long operations
- Add early termination for oversized data sets
- Use backpressure handling for high-frequency events
- Optimize O(n²) algorithms for large datasets

### Memory Management
- Clean up resources properly
- Use weak references where appropriate
- Avoid memory leaks in long-running services
- Profile performance-critical paths

## Communication Style
- Be concise and direct (under 4 lines typically)
- Don't add unnecessary preamble or explanations
- Focus on answering the specific question asked
- One-word answers are fine when appropriate
- Avoid "Here is what I will do" or similar verbose intros
- Don't mention AI, assistant names, or automated capabilities in responses

## Version & Release Management

### Release Cycle Process
Complete release process:
1. Bump version in `app/build.gradle.kts` (versionName field)
2. Commit and push the version bump
3. Close current milestone in GitHub
4. Create GitHub release with auto-generated release notes (tag auto-created)
5. Watch the Play Release workflow run with `gh run watch` and do not close the terminal until it reports success — a failure here means the signed AAB never reached Google Play, so the GitHub release exists but no update rolls out to users. Investigate and rerun before walking away.

### Version Bumps
- Update version in build.gradle.kts
- Create and close milestones appropriately
- Use semantic versioning
- Create GitHub releases with auto-generated notes

### Milestone Management
- Create milestones for versions (v2.1.4, v2.1.5, etc.)
- Assign issues to appropriate milestones
- Close milestones when versions are released
- Use milestone descriptions for release planning

## Emergency Fixes
For critical issues like ANRs:
1. Identify root cause through code analysis
2. Implement targeted fixes with minimal scope
3. Add timeout mechanisms and early termination
4. Test compilation immediately
5. Create focused PRs with clear problem/solution description

## External Integrations
- Minimize external dependencies where possible
- Remove unnecessary telemetry (like Sentry) for privacy
- Use standard Android APIs over third-party when feasible
- Document any required external service configurations
