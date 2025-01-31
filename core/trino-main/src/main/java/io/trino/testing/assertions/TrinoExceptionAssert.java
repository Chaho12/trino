/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.testing.assertions;

import io.trino.client.ErrorInfo;
import io.trino.client.FailureInfo;
import io.trino.spi.ErrorCode;
import io.trino.spi.ErrorCodeSupplier;
import io.trino.spi.ErrorType;
import io.trino.spi.Location;
import io.trino.spi.TrinoException;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.assertj.core.internal.Failures;
import org.assertj.core.util.CheckReturnValue;

import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.assertj.core.error.ShouldContainCharSequence.shouldContain;
import static org.assertj.core.error.ShouldHaveMessageMatchingRegex.shouldHaveMessageMatchingRegex;

public final class TrinoExceptionAssert
        extends AbstractThrowableAssert<TrinoExceptionAssert, Throwable>
{
    private final FailureInfo failureInfo;

    @CheckReturnValue
    public static TrinoExceptionAssert assertTrinoExceptionThrownBy(ThrowingCallable throwingCallable)
    {
        Throwable throwable = catchThrowable(throwingCallable);
        if (throwable == null) {
            failBecauseExceptionWasNotThrown(TrinoException.class);
        }
        return assertThatTrinoException(throwable);
    }

    @CheckReturnValue
    public static TrinoExceptionAssert assertThatTrinoException(Throwable throwable)
    {
        Optional<FailureInfo> failureInfo = TestUtil.getFailureInfo(throwable);
        if (failureInfo.isEmpty()) {
            throw new AssertionError("Expected TrinoException or wrapper, but got: " + throwable.getClass().getName() + " " + throwable);
        }
        return new TrinoExceptionAssert(throwable, failureInfo.get());
    }

    private TrinoExceptionAssert(Throwable actual, FailureInfo failureInfo)
    {
        super(actual, TrinoExceptionAssert.class);
        this.failureInfo = requireNonNull(failureInfo, "failureInfo is null");
    }

    public TrinoExceptionAssert hasErrorCode(ErrorCodeSupplier... errorCodeSupplier)
    {
        ErrorCode errorCode = null;
        ErrorInfo errorInfo = failureInfo.getErrorInfo();
        if (errorInfo != null) {
            errorCode = new ErrorCode(errorInfo.getCode(), errorInfo.getName(), ErrorType.valueOf(errorInfo.getType()));
        }

        try {
            assertThat(errorCode).isIn(
                    Stream.of(errorCodeSupplier)
                            .map(ErrorCodeSupplier::toErrorCode)
                            .collect(toSet()));
        }
        catch (AssertionError e) {
            e.addSuppressed(actual);
            throw e;
        }
        return myself;
    }

    public TrinoExceptionAssert hasLocation(int lineNumber, int columnNumber)
    {
        try {
            Optional<Location> location = Optional.ofNullable(failureInfo.getErrorLocation())
                    .map(errorLocation -> new Location(errorLocation.getLineNumber(), errorLocation.getColumnNumber()));
            assertThat(location).hasValue(new Location(lineNumber, columnNumber));
        }
        catch (AssertionError e) {
            e.addSuppressed(actual);
            throw e;
        }
        return myself;
    }

    public TrinoExceptionAssert hasCauseMessageMatching(String regex)
    {
        Throwable cause = actual;
        while (cause != null) {
            if (cause.getMessage().matches(regex)) {
                return myself;
            }
            cause = cause.getCause();
        }
        throw Failures.instance().failure(info, shouldHaveMessageMatchingRegex(actual, regex));
    }

    public TrinoExceptionAssert hasCauseMessageContaining(String message)
    {
        Throwable cause = actual;
        while (cause != null) {
            if (cause.getMessage().contains(message)) {
                return myself;
            }
            cause = cause.getCause();
        }
        throw Failures.instance().failure(info, shouldContain(actual, message));
    }
}
