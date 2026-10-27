package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.common_clear
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource

/** How an [AppTextField] looks: a form field, a search pill, or a field inside another surface (a glass bar). */
enum class AppTextFieldStyle(internal val minHeight: Dp) {
    /** A 56 dp rounded field in a form or sheet; it grows with its text when its line limits allow. */
    Field(56.dp),

    /** A 48 dp pill with a search icon and the keyboard's search key, for search as you type. */
    Search(48.dp),

    /** A 48 dp field without a background of its own, inside a bar that already draws one. */
    Inline(48.dp),
}

/**
 * The app's one text field. Its text lives in a [TextFieldState] held by the screen's view model (as a property, or
 * in the state of the dialog or form that owns it), so typing never waits for the screen state and the cursor stays
 * where the user puts it. The view model reads the text when it needs it (save, send); a field whose typing starts
 * work (search as you type) also reports each change through [onTextChange].
 *
 * The keyboard's key does the same everywhere: Next moves to the next field; Done, Search, Go and Send close the
 * keyboard, after running [onImeAction] (submit, add) when there is one. A ✕ shows while the field is focused or has
 * text: it closes the keyboard and clears the field, which [onTextChange] reports as "" so the view model puts what
 * depends on it back to the start. [clearable] false hides it.
 */
@Composable
fun AppTextField(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
    style: AppTextFieldStyle = AppTextFieldStyle.Field,
    onTextChange: ((String) -> Unit)? = null,
    clearable: Boolean = true,
    onImeAction: (() -> Unit)? = null,
    leadingIcon: ImageVector? = if (style == AppTextFieldStyle.Search) Icons.Default.Search else null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = if (style == AppTextFieldStyle.Search) SearchKeyboard else KeyboardOptions(imeAction = ImeAction.Done),
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    inputTransformation: InputTransformation? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val currentOnTextChange by rememberUpdatedState(onTextChange)
    val currentOnImeAction by rememberUpdatedState(onImeAction)
    // The view model already knows the text the field opens with; only what the user changes afterwards is sent.
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.drop(1).collect { currentOnTextChange?.invoke(it) }
    }
    val showClear = clearable && enabled && (isFocused || state.text.isNotEmpty())
    val shape: Shape = if (style == AppTextFieldStyle.Field) MaterialTheme.shapes.extraLarge else CircleShape
    val container = if (style == AppTextFieldStyle.Inline) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant
    val vertical = (style.minHeight - LineHeight) / 2
    TextField(
        state = state,
        modifier = modifier.heightIn(min = style.minHeight),
        enabled = enabled,
        textStyle = textStyle,
        placeholder = { Text(placeholder, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(IconSize)) } },
        trailingIcon = if (trailing != null || showClear) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailing?.invoke()
                    if (showClear) {
                        IconButton(onClick = {
                            focusManager.clearFocus()
                            state.clearText()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.common_clear), modifier = Modifier.size(IconSize))
                        }
                    }
                }
            }
        } else {
            null
        },
        isError = isError,
        inputTransformation = inputTransformation,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = KeyboardActionHandler { performDefaultAction ->
            currentOnImeAction?.invoke()
            when (keyboardOptions.imeAction) {
                ImeAction.Next, ImeAction.Previous -> performDefaultAction()
                else -> focusManager.clearFocus()
            }
        },
        lineLimits = lineLimits,
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            disabledContainerColor = container,
            errorContainerColor = if (style == AppTextFieldStyle.Inline) container else MaterialTheme.colorScheme.errorContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.medium, vertical = vertical),
        interactionSource = interactionSource,
    )
}

/** Search as you type: no capitals or corrections, and the search key closes the keyboard. */
val SearchKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search)

/**
 * A field that opens a picker instead of the keyboard (dates, a birth date): it looks like an [AppTextField],
 * showing [text] or, while that's null, [placeholder], with an optional [trailing] note ("5 days").
 */
@Composable
fun FieldButton(
    text: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = AppTextFieldStyle.Field.minHeight),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall),
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) { leadingIcon?.invoke() }
            Text(
                text = text ?: placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = if (text == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) { trailing?.invoke() }
        }
    }
}

/** One line of body text, which the padding centres in the field's height. */
private val LineHeight = 24.dp
private val IconSize = 20.dp

@Composable
private fun AppTextFieldPreviewContent() {
    Column(
        Modifier.background(MaterialTheme.colorScheme.surface).padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        AppTextField(rememberTextFieldState(), "Search trips…", Modifier.fillMaxWidth(), style = AppTextFieldStyle.Search)
        AppTextField(rememberTextFieldState("Lisbon"), "Search people…", Modifier.fillMaxWidth(), style = AppTextFieldStyle.Search)
        AppTextField(rememberTextFieldState(), "Give it a name", Modifier.fillMaxWidth())
        AppTextField(rememberTextFieldState("Summer in Vienna"), "Trip name", Modifier.fillMaxWidth())
        AppTextField(rememberTextFieldState("taken.name"), "yourname", Modifier.fillMaxWidth(), isError = true)
        AppTextField(
            rememberTextFieldState("A comment that goes on for a while and wraps onto a second line"),
            "Add a comment",
            Modifier.fillMaxWidth(),
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
        )
        FieldButton("19 – 23 Oct 2026", "Add dates", {}, leadingIcon = { Icon(Icons.Default.DateRange, null) }, trailing = { Text("5 days", style = MaterialTheme.typography.labelMedium) })
        FieldButton(null, "Add dates", {}, leadingIcon = { Icon(Icons.Default.DateRange, null) })
    }
}

@Preview
@Composable
private fun AppTextFieldPreview() {
    TripinglyTheme { AppTextFieldPreviewContent() }
}

@Preview
@Composable
private fun AppTextFieldDarkPreview() {
    TripinglyTheme(darkTheme = true) { AppTextFieldPreviewContent() }
}
