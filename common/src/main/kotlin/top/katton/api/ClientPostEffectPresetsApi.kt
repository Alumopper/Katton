@file:Suppress("unused")

package top.katton.api

/**
 * Register a configurable full-screen invert effect.
 *
 * @param amount 0.0 keeps the original image, 1.0 fully inverts it.
 */
fun registerClientInvertPostEffect(
    id: String = "katton:preset/invert",
    amount: Float = 1.0f,
    activate: Boolean = false
): Boolean = registerClientPostEffectPreset(id, activate, invertPostEffectShader(amount))

/**
 * Register a configurable grayscale effect.
 *
 * @param amount 0.0 keeps color, 1.0 is fully grayscale.
 */
fun registerClientGrayscalePostEffect(
    id: String = "katton:preset/grayscale",
    amount: Float = 1.0f,
    activate: Boolean = false
): Boolean = registerClientPostEffectPreset(id, activate, grayscalePostEffectShader(amount))

/**
 * Register a configurable sepia effect.
 *
 * @param amount 0.0 keeps the original image, 1.0 is fully sepia.
 */
fun registerClientSepiaPostEffect(
    id: String = "katton:preset/sepia",
    amount: Float = 1.0f,
    activate: Boolean = false
): Boolean = registerClientPostEffectPreset(id, activate, sepiaPostEffectShader(amount))

/**
 * Register a tint effect that maps screen luminance toward [color].
 *
 * @param color RGB or ARGB integer. Alpha is ignored.
 * @param amount 0.0 keeps original color, 1.0 fully applies the tint.
 */
fun registerClientTintPostEffect(
    id: String = "katton:preset/tint",
    color: Int = 0x55AAFF,
    amount: Float = 0.5f,
    activate: Boolean = false
): Boolean = registerClientPostEffectPreset(id, activate, tintPostEffectShader(color, amount))

/**
 * Register a basic color adjustment effect.
 *
 * @param brightness -1.0 darkens, 0.0 unchanged, 1.0 brightens.
 * @param contrast 1.0 unchanged, lower flattens, higher increases contrast.
 * @param saturation 1.0 unchanged, 0.0 grayscale, higher oversaturates.
 * @param gamma 1.0 unchanged. Higher values lift shadows.
 */
fun registerClientColorAdjustPostEffect(
    id: String = "katton:preset/color_adjust",
    brightness: Float = 0.0f,
    contrast: Float = 1.0f,
    saturation: Float = 1.0f,
    gamma: Float = 1.0f,
    activate: Boolean = false
): Boolean = registerClientPostEffectPreset(
    id,
    activate,
    colorAdjustPostEffectShader(brightness, contrast, saturation, gamma)
)

/**
 * Register a vignette effect.
 *
 * @param amount strength of the edge color.
 * @param radius distance from screen center before the vignette starts.
 * @param softness width of the falloff.
 * @param color RGB or ARGB integer. Alpha is ignored.
 */
fun registerClientVignettePostEffect(
    id: String = "katton:preset/vignette",
    amount: Float = 0.75f,
    radius: Float = 0.65f,
    softness: Float = 0.35f,
    color: Int = 0x000000,
    activate: Boolean = false
): Boolean = registerClientPostEffectPreset(id, activate, vignettePostEffectShader(amount, radius, softness, color))

/**
 * Register a chromatic aberration effect.
 *
 * @param offsetPixels red/blue channel offset in screen pixels.
 * @param amount blend amount between original and shifted color.
 */
fun registerClientChromaticAberrationPostEffect(
    id: String = "katton:preset/chromatic_aberration",
    offsetPixels: Float = 1.5f,
    amount: Float = 1.0f,
    activate: Boolean = false
): Boolean = registerClientPostEffectPreset(
    id,
    activate,
    chromaticAberrationPostEffectShader(offsetPixels, amount)
)

/**
 * Register a pixelation effect.
 *
 * @param pixelSize block size in screen pixels.
 */
fun registerClientPixelatePostEffect(
    id: String = "katton:preset/pixelate",
    pixelSize: Float = 4.0f,
    activate: Boolean = false
): Boolean = registerClientPostEffectPreset(id, activate, pixelatePostEffectShader(pixelSize))

/**
 * Register a posterize effect.
 *
 * @param levels number of color steps per channel.
 * @param amount 0.0 keeps original color, 1.0 fully posterizes.
 */
fun registerClientPosterizePostEffect(
    id: String = "katton:preset/posterize",
    levels: Int = 5,
    amount: Float = 1.0f,
    activate: Boolean = false
): Boolean = registerClientPostEffectPreset(id, activate, posterizePostEffectShader(levels, amount))

/**
 * Register a static scanline effect.
 *
 * @param amount scanline darkness.
 * @param lines approximate number of scanlines across the screen height.
 */
fun registerClientScanlinePostEffect(
    id: String = "katton:preset/scanline",
    amount: Float = 0.18f,
    lines: Float = 240.0f,
    activate: Boolean = false
): Boolean = registerClientPostEffectPreset(id, activate, scanlinePostEffectShader(amount, lines))

/**
 * Register a blur effect backed by Minecraft's built-in box blur shader.
 *
 * @param radius blur radius in pixels.
 * @param passes number of horizontal/vertical blur rounds.
 */
fun registerClientBlurPostEffect(
    id: String = "katton:preset/blur",
    radius: Float = 4.0f,
    passes: Int = 1,
    activate: Boolean = false
): Boolean {
    val registered = registerClientPostEffect(id, blurPostEffectJson(radius, passes))
    if (registered && activate) {
        setClientPostEffect(id)
    }
    return registered
}

private fun registerClientPostEffectPreset(
    id: String,
    activate: Boolean,
    fragmentShaderSource: String
): Boolean {
    val registered = registerSimpleClientPostEffect(id, fragmentShaderSource)
    if (registered && activate) {
        setClientPostEffect(id)
    }
    return registered
}

private fun invertPostEffectShader(amount: Float): String {
    val amountValue = shaderFloat(normalized(amount, 1.0f, 0.0f, 1.0f))
    return presetShader(
        """
        const float Amount = $amountValue;

        void main() {
            vec4 color = texture(InSampler, texCoord);
            vec3 result = mix(color.rgb, vec3(1.0) - color.rgb, Amount);
            fragColor = vec4(result, color.a);
        }
        """.trimIndent()
    )
}

private fun grayscalePostEffectShader(amount: Float): String {
    val amountValue = shaderFloat(normalized(amount, 1.0f, 0.0f, 1.0f))
    return presetShader(
        """
        const float Amount = $amountValue;

        void main() {
            vec4 color = texture(InSampler, texCoord);
            float gray = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
            vec3 result = mix(color.rgb, vec3(gray), Amount);
            fragColor = vec4(result, color.a);
        }
        """.trimIndent()
    )
}

private fun sepiaPostEffectShader(amount: Float): String {
    val amountValue = shaderFloat(normalized(amount, 1.0f, 0.0f, 1.0f))
    return presetShader(
        """
        const float Amount = $amountValue;

        void main() {
            vec4 color = texture(InSampler, texCoord);
            vec3 sepia = vec3(
                dot(color.rgb, vec3(0.393, 0.769, 0.189)),
                dot(color.rgb, vec3(0.349, 0.686, 0.168)),
                dot(color.rgb, vec3(0.272, 0.534, 0.131))
            );
            vec3 result = mix(color.rgb, clamp(sepia, 0.0, 1.0), Amount);
            fragColor = vec4(result, color.a);
        }
        """.trimIndent()
    )
}

private fun tintPostEffectShader(color: Int, amount: Float): String {
    val tint = shaderVec3(color)
    val amountValue = shaderFloat(normalized(amount, 0.5f, 0.0f, 1.0f))
    return presetShader(
        """
        const vec3 TintColor = vec3($tint);
        const float Amount = $amountValue;

        void main() {
            vec4 color = texture(InSampler, texCoord);
            float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
            vec3 tinted = luminance * TintColor;
            vec3 result = mix(color.rgb, tinted, Amount);
            fragColor = vec4(result, color.a);
        }
        """.trimIndent()
    )
}

private fun colorAdjustPostEffectShader(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    gamma: Float
): String {
    val brightnessValue = shaderFloat(normalized(brightness, 0.0f, -1.0f, 1.0f))
    val contrastValue = shaderFloat(normalized(contrast, 1.0f, 0.0f, 3.0f))
    val saturationValue = shaderFloat(normalized(saturation, 1.0f, 0.0f, 3.0f))
    val gammaValue = shaderFloat(normalized(gamma, 1.0f, 0.05f, 5.0f))
    return presetShader(
        """
        const float Brightness = $brightnessValue;
        const float Contrast = $contrastValue;
        const float Saturation = $saturationValue;
        const float Gamma = $gammaValue;

        void main() {
            vec4 color = texture(InSampler, texCoord);
            vec3 adjusted = color.rgb + vec3(Brightness);
            adjusted = (adjusted - vec3(0.5)) * Contrast + vec3(0.5);
            float gray = dot(adjusted, vec3(0.2126, 0.7152, 0.0722));
            adjusted = mix(vec3(gray), adjusted, Saturation);
            adjusted = pow(max(adjusted, vec3(0.0)), vec3(1.0 / Gamma));
            fragColor = vec4(clamp(adjusted, 0.0, 1.0), color.a);
        }
        """.trimIndent()
    )
}

private fun vignettePostEffectShader(amount: Float, radius: Float, softness: Float, color: Int): String {
    val amountValue = shaderFloat(normalized(amount, 0.75f, 0.0f, 1.0f))
    val radiusValue = shaderFloat(normalized(radius, 0.65f, 0.0f, 1.5f))
    val softnessValue = shaderFloat(normalized(softness, 0.35f, 0.001f, 1.5f))
    val vignetteColor = shaderVec3(color)
    return presetShader(
        """
        const float Amount = $amountValue;
        const float Radius = $radiusValue;
        const float Softness = $softnessValue;
        const vec3 VignetteColor = vec3($vignetteColor);

        void main() {
            vec4 color = texture(InSampler, texCoord);
            vec2 centered = texCoord - vec2(0.5);
            float distanceFromCenter = length(centered) * 1.41421356;
            float edge = smoothstep(Radius, Radius + Softness, distanceFromCenter);
            vec3 result = mix(color.rgb, VignetteColor, edge * Amount);
            fragColor = vec4(result, color.a);
        }
        """.trimIndent()
    )
}

private fun chromaticAberrationPostEffectShader(offsetPixels: Float, amount: Float): String {
    val offsetValue = shaderFloat(normalized(offsetPixels, 1.5f, 0.0f, 64.0f))
    val amountValue = shaderFloat(normalized(amount, 1.0f, 0.0f, 1.0f))
    return presetShader(
        """
        const float OffsetPixels = $offsetValue;
        const float Amount = $amountValue;

        void main() {
            vec4 color = texture(InSampler, texCoord);
            vec2 offset = vec2(OffsetPixels, 0.0) / max(InSize, vec2(1.0));
            vec3 shifted = vec3(
                texture(InSampler, texCoord + offset).r,
                color.g,
                texture(InSampler, texCoord - offset).b
            );
            vec3 result = mix(color.rgb, shifted, Amount);
            fragColor = vec4(result, color.a);
        }
        """.trimIndent()
    )
}

private fun pixelatePostEffectShader(pixelSize: Float): String {
    val pixelSizeValue = shaderFloat(normalized(pixelSize, 4.0f, 1.0f, 256.0f))
    return presetShader(
        """
        const float PixelSize = $pixelSizeValue;

        void main() {
            vec2 pixel = vec2(PixelSize);
            vec2 safeSize = max(InSize, vec2(1.0));
            vec2 pixelUv = (floor(texCoord * safeSize / pixel) * pixel + pixel * 0.5) / safeSize;
            fragColor = texture(InSampler, pixelUv);
        }
        """.trimIndent()
    )
}

private fun posterizePostEffectShader(levels: Int, amount: Float): String {
    val levelsValue = shaderFloat(levels.coerceIn(2, 64).toFloat())
    val amountValue = shaderFloat(normalized(amount, 1.0f, 0.0f, 1.0f))
    return presetShader(
        """
        const float Levels = $levelsValue;
        const float Amount = $amountValue;

        void main() {
            vec4 color = texture(InSampler, texCoord);
            vec3 posterized = floor(color.rgb * (Levels - 1.0) + 0.5) / (Levels - 1.0);
            vec3 result = mix(color.rgb, posterized, Amount);
            fragColor = vec4(result, color.a);
        }
        """.trimIndent()
    )
}

private fun scanlinePostEffectShader(amount: Float, lines: Float): String {
    val amountValue = shaderFloat(normalized(amount, 0.18f, 0.0f, 1.0f))
    val linesValue = shaderFloat(normalized(lines, 240.0f, 1.0f, 4096.0f))
    return presetShader(
        """
        const float Amount = $amountValue;
        const float Lines = $linesValue;

        void main() {
            vec4 color = texture(InSampler, texCoord);
            float wave = sin(texCoord.y * Lines * 6.2831853) * 0.5 + 0.5;
            float mask = mix(1.0 - Amount, 1.0, wave);
            fragColor = vec4(color.rgb * mask, color.a);
        }
        """.trimIndent()
    )
}

private fun blurPostEffectJson(radius: Float, passes: Int): String {
    val radiusValue = shaderFloat(normalized(radius, 4.0f, 0.0f, 64.0f))
    val passCount = passes.coerceIn(1, 8)
    val passJson = mutableListOf<String>()

    repeat(passCount) {
        passJson += blurPassJson("minecraft:main", "swap", 1.0f, 0.0f, radiusValue)
        passJson += blurPassJson("swap", "minecraft:main", 0.0f, 1.0f, radiusValue)
    }

    return """
        {
          "targets": {
            "swap": {}
          },
          "passes": [
            ${passJson.joinToString(",\n")}
          ]
        }
    """.trimIndent()
}

private fun blurPassJson(inputTarget: String, outputTarget: String, dirX: Float, dirY: Float, radiusValue: String): String =
    """
    {
      "vertex_shader": "minecraft:core/screenquad",
      "fragment_shader": "minecraft:post/box_blur",
      "inputs": [
        {
          "sampler_name": "In",
          "target": "$inputTarget",
          "bilinear": true
        }
      ],
      "output": "$outputTarget",
      "uniforms": {
        "BlurConfig": [
          {
            "name": "BlurDir",
            "type": "vec2",
            "value": [${shaderFloat(dirX)}, ${shaderFloat(dirY)}]
          },
          {
            "name": "Radius",
            "type": "float",
            "value": $radiusValue
          }
        ]
      }
    }
    """.trimIndent()

private fun presetShader(body: String): String =
    """
    #version 330

    uniform sampler2D InSampler;

    layout(std140) uniform SamplerInfo {
        vec2 OutSize;
        vec2 InSize;
    };

    in vec2 texCoord;

    out vec4 fragColor;

    $body
    """.trimIndent()

private fun shaderVec3(color: Int): String {
    val red = ((color ushr 16) and 0xFF) / 255.0f
    val green = ((color ushr 8) and 0xFF) / 255.0f
    val blue = (color and 0xFF) / 255.0f
    return "${shaderFloat(red)}, ${shaderFloat(green)}, ${shaderFloat(blue)}"
}

private fun shaderFloat(value: Float): String {
    val number = if (value.isNaN() || value.isInfinite() || value == -0.0f) 0.0f else value
    val text = java.lang.Float.toString(number)
    return if ('.' in text || 'E' in text || 'e' in text) text else "$text.0"
}

private fun normalized(value: Float, fallback: Float, min: Float, max: Float): Float {
    val number = if (value.isNaN() || value.isInfinite()) fallback else value
    return number.coerceIn(min, max)
}
