package com.example.testario.ui.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField as MaterialOutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Campo de texto de NEXUM: relleno tonal sin marco; el latón solo aparece al enfocar y el
 * terracota al fallar. Las pantallas lo importan con el nombre `OutlinedTextField`, así que
 * cambia todos los campos a la vez. `colors` se acepta por compatibilidad y se ignora.
 */
@Composable
private fun neonFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = TestarioNeonSurface,
    unfocusedContainerColor = TestarioNeonSurface,
    disabledContainerColor = TestarioNeonSurface.copy(alpha = 0.5f),
    errorContainerColor = TestarioNeonSurface,
    focusedBorderColor = TestarioPrimary,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    errorBorderColor = TestarioRed,
    focusedTextColor = TestarioText,
    unfocusedTextColor = TestarioText,
    disabledTextColor = TestarioMutedText,
    errorTextColor = TestarioText,
    focusedLabelColor = TestarioPrimary,
    unfocusedLabelColor = TestarioMutedText,
    disabledLabelColor = TestarioMutedText.copy(alpha = 0.6f),
    errorLabelColor = TestarioRed,
    cursorColor = TestarioPrimary,
    errorCursorColor = TestarioRed,
    focusedPlaceholderColor = TestarioMutedText,
    unfocusedPlaceholderColor = TestarioMutedText,
    focusedLeadingIconColor = TestarioMutedText,
    unfocusedLeadingIconColor = TestarioMutedText,
    focusedTrailingIconColor = TestarioMutedText,
    unfocusedTrailingIconColor = TestarioMutedText,
    focusedSupportingTextColor = TestarioMutedText,
    unfocusedSupportingTextColor = TestarioMutedText,
    errorSupportingTextColor = TestarioRed
)

private val NeonFieldShape = RoundedCornerShape(14.dp)

@Composable
fun NeonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = NeonFieldShape,
    @Suppress("UNUSED_PARAMETER") colors: TextFieldColors = OutlinedTextFieldDefaults.colors()
) {
    MaterialOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = shape,
        colors = neonFieldColors()
    )
}

@Composable
fun NeonTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = NeonFieldShape,
    @Suppress("UNUSED_PARAMETER") colors: TextFieldColors = OutlinedTextFieldDefaults.colors()
) {
    MaterialOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = shape,
        colors = neonFieldColors()
    )
}
