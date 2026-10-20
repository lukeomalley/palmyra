package xyz.lukeomalley.palmyra

/** Host-side asset generation and regression oracle. Three box passes, clamped at the edges. */
@Suppress("MagicNumber")
object StackBlur {
    fun blur(
        pixels: IntArray,
        width: Int,
        height: Int,
        radius: Int,
    ): IntArray {
        require(width > 0 && height > 0 && pixels.size == width * height && radius >= 0)
        var result = pixels.copyOf()
        repeat(3) {
            result = pass(result, width, height, radius, true)
            result = pass(result, width, height, radius, false)
        }
        return result
    }

    private fun pass(
        input: IntArray,
        width: Int,
        height: Int,
        radius: Int,
        horizontal: Boolean,
    ): IntArray {
        val output = IntArray(input.size)
        val length = if (horizontal) width else height
        val lines = if (horizontal) height else width
        val divisor = radius * 2 + 1
        for (line in 0 until lines) {
            val sums = IntArray(4)

            fun offset(pos: Int) =
                if (horizontal) {
                    line * width + pos.coerceIn(0, length - 1)
                } else {
                    pos.coerceIn(0, length - 1) * width +
                        line
                }

            fun accumulate(
                pixel: Int,
                sign: Int,
            ) {
                for (channel in 0..3) sums[channel] += ((pixel ushr (channel * 8)) and 255) * sign
            }
            for (pos in -radius..radius) accumulate(input[offset(pos)], 1)
            for (pos in 0 until length) {
                var color = 0
                for (channel in 0..3) color = color or ((sums[channel] / divisor) shl (channel * 8))
                output[offset(pos)] = color
                accumulate(input[offset(pos - radius)], -1)
                accumulate(input[offset(pos + radius + 1)], 1)
            }
        }
        return output
    }
}
