# Summary: Integration Tests Use Both Jaeger SDK and Zipkin - Analysis Complete

## Question Answered
**Does it make any difference in how the dependency links are calculated?**

## Answer: NO - With Proper Handling

The integration tests use both legacy Jaeger SDK and Zipkin **by design** to ensure the Spark dependency job correctly handles traces from both systems. While the span models differ significantly, the dependency calculation logic is sophisticated enough to produce **identical dependency graphs** from both.

## Key Findings

### 1. Different Span Models Require Different Processing

**Jaeger Model (Individual Spans):**
- Each service operation creates a unique span
- Parent-child relationships via explicit references
- Simpler to process: follow reference chains

**Zipkin Model (Shared Spans):**
- Client and server can share the same span ID
- Uses span.kind tags (CLIENT, SERVER, PRODUCER, CONSUMER)
- Requires deduplication logic to avoid double-counting

### 2. The Code Handles Both Models

`SpansToDependencyLinks.java` contains three processing stages:

1. **Shared Span Detection** (lines 70-76)
   - Groups spans by span ID
   - Detects when multiple spans share an ID (Zipkin pattern)

2. **Zipkin Shared Span Processing** (lines 78-79, 164-184)
   - Extracts dependencies from shared spans
   - Matches CLIENT/PRODUCER with SERVER/CONSUMER tags
   - Creates dependency: client_service → server_service

3. **Individual Span Processing** (lines 81-120)
   - Processes Jaeger spans and non-shared Zipkin spans
   - Follows parent references to create dependencies
   - Skips server-side of shared spans to avoid duplication

### 3. Test Infrastructure Ensures Consistency

`DependencyLinkDerivator.java` (test helper) compensates for Zipkin's extra client spans:
- Adds internal service calls to expected dependencies for Zipkin
- Ensures both test paths expect the same results

### 4. Real-World Necessity

Tests with both SDKs because:
- Jaeger backend accepts both formats (ports 14268 and 9411)
- Production deployments may have mixed clients
- Edge cases in shared span handling need verification
- Historical issue tracked in [jaegertracing/jaeger#451](https://github.com/jaegertracing/jaeger/issues/451)

## Files Modified

1. **INTEGRATION_TEST_ANALYSIS.md** (new)
   - Comprehensive analysis document
   - Detailed explanation of span models
   - Code examples and evidence

2. **SpansToDependencyLinks.java**
   - Added comments explaining shared span detection
   - Documented why CLIENT spans are preferred
   - Explained different processing paths
   - Added javadoc for `sharedSpanDependency()` method

3. **TracersGenerator.java**
   - Added comprehensive class-level documentation
   - Explained why both SDKs are necessary
   - Listed specific reasons and benefits

## Verification

✅ Unit tests pass: `SpansToDependencyLinksTest` (3/3 tests)
- Tests verify correct handling of different span configurations
- No changes to behavior, only documentation

## Conclusion

Using both SDKs is **intentional, necessary, and does not affect the correctness of results**. The dependency calculation logic is designed to normalize differences between the two span models and produce consistent dependency graphs regardless of which tracing SDK generated the spans.

This approach:
- ✅ Validates real-world scenarios
- ✅ Ensures comprehensive test coverage
- ✅ Prevents regressions in shared span handling
- ✅ Maintains compatibility with heterogeneous tracing clients
