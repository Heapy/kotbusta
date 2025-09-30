package io.heapy.kotbusta.mapper

/**
 * Maps from input type I to output type O (left-to-right direction).
 */
fun interface LeftTypeMapper<I, O> {
    fun mapLeft(input: I): O
}

/**
 * Maps from output type O to input type I (right-to-left direction).
 */
fun interface RightTypeMapper<I, O> {
    fun mapRight(output: O): I
}

/**
 * Bidirectional type mapper that implements both left-to-right and right-to-left mappings.
 */
interface TypeMapper<I, O> : LeftTypeMapper<I, O>, RightTypeMapper<I, O>

/**
 * Creates a bidirectional type mapper by combining left and right mappers.
 */
fun <I, O> TypeMapper(
    left: (I) -> O,
    right: (O) -> I,
): TypeMapper<I, O> = object : TypeMapper<I, O> {
    override fun mapLeft(input: I): O = left(input)
    override fun mapRight(output: O): I = right(output)
}

@Suppress("NOTHING_TO_INLINE")
inline infix fun <I, O> I.mapUsing(mapper: LeftTypeMapper<I, O>): O =
    mapper.mapLeft(this)

@Suppress("NOTHING_TO_INLINE")
inline infix fun <O, I> O.mapUsing(mapper: RightTypeMapper<I, O>): I =
    mapper.mapRight(this)

/**
 * Verifies bidirectional mapping correctness by checking that:
 * - input -> output -> input returns the original input
 * - output -> input -> output returns the original output
 *
 * @throws IllegalArgumentException if any round-trip conversion fails
 */
fun <I, O> TypeMapper<I, O>.verifyBidirectional(
    inputs: List<I>,
    outputs: List<O>,
) {
    // Verify input -> output -> input
    inputs.forEach { input ->
        val roundTrip = input mapUsing this mapUsing this
        require(roundTrip == input) {
            "Round-trip failed for input: $input. Got: $roundTrip"
        }
    }

    // Verify output -> input -> output
    outputs.forEach { output ->
        val roundTrip = output mapUsing this mapUsing this
        require(roundTrip == output) {
            "Round-trip failed for output: $output. Got: $roundTrip"
        }
    }
}
