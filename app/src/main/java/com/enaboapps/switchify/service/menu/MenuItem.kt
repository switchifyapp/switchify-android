package com.enaboapps.switchify.service.menu

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TextView.AUTO_SIZE_TEXT_TYPE_NONE
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.utils.Logger

/**
 * This class represents a menu item
 * @property id The id of the menu item
 * @property text The text of the menu item
 * @property drawableId The drawable resource id of the menu item
 * @property drawableDescription The description of the drawable
 * @property showDrawableDescription Whether to show the drawable description
 * @property isSmall Whether the menu item is small
 * @property closeOnSelect Whether the menu should close when the item is selected
 * @property isLinkToMenu Whether the item is a link to another menu
 * @property isMenuHierarchyManipulator Whether the item manipulates the menu hierarchy
 * @property action The action to perform when the item is selected
 */
class MenuItem(
    val id: String,
    val text: String = "",
    private val drawableId: Int = 0,
    val drawableDescription: String = "",
    val showDrawableDescription: Boolean = true,
    val isSmall: Boolean = false,
    val closeOnSelect: Boolean = true,
    var isLinkToMenu: Boolean = false,
    var isMenuHierarchyManipulator: Boolean = false,
    private val action: () -> Unit
) {
    /**
     * The view of the menu item
     */
    private var view: LinearLayout? = null

    /**
     * The image view of the menu item
     */
    private var imageView: ImageView? = null

    /**
     * The text view for the drawable description
     */
    private var drawableDescriptionTextView: TextView? = null

    /**
     * The text view of the menu item
     */
    private var textView: TextView? = null

    private val backgroundColor = Color.BLACK
    private val foregroundColor = Color.WHITE

    /**
     * Inflate the menu item
     * @param linearLayout The linear layout to inflate the menu item into
     */
    fun inflate(linearLayout: LinearLayout) {
        val menuSizeManager = MenuSizeManager(linearLayout.context)

        val screenWidth = ScreenUtils.getWidth(linearLayout.context)
        val itemsPerRow = menuSizeManager.getMenuSize().itemsPerPage / 2

        var widthPx =
            ScreenUtils.dpToPx(linearLayout.context, menuSizeManager.getMenuSize().itemWidth)
        var heightPx =
            ScreenUtils.dpToPx(linearLayout.context, menuSizeManager.getMenuSize().itemHeight)

        if (isSmall) {
            widthPx = ScreenUtils.dpToPx(
                linearLayout.context,
                menuSizeManager.getMenuSize().itemWidthSmall
            )
            heightPx = ScreenUtils.dpToPx(
                linearLayout.context,
                menuSizeManager.getMenuSize().itemHeightSmall
            )
        }

        // If width x itemsPerRow is greater than screen width, adjust the width to fit
        if (widthPx * itemsPerRow > screenWidth) {
            widthPx = screenWidth / itemsPerRow
        }

        // If using a drawable description, adjust the height to accommodate the description
        if (drawableDescription.isNotEmpty()) {
            heightPx += ScreenUtils.dpToPx(linearLayout.context, 20)
        }

        view = LinearLayout(linearLayout.context).apply {
            layoutParams = LinearLayout.LayoutParams(widthPx, heightPx).apply {
                weight = 1f
            }
            minimumWidth = widthPx
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(backgroundColor)
            setOnClickListener { select() }
        }

        val padding = 20

        if (drawableId != 0) {
            imageView = ImageView(linearLayout.context).apply {
                val wrappedDrawable = DrawableCompat.wrap(
                    ResourcesCompat.getDrawable(
                        linearLayout.context.resources,
                        drawableId,
                        null
                    )!!
                ).mutate()
                DrawableCompat.setTint(
                    wrappedDrawable,
                    foregroundColor
                )
                setImageDrawable(wrappedDrawable)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(padding, padding, padding, padding)
                view?.addView(this)
            }
        }

        if (text.isNotEmpty()) {
            textView = TextView(linearLayout.context).apply {
                text = this@MenuItem.text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, menuSizeManager.getMenuSize().textSize)
                setAutoSizeTextTypeWithDefaults(AUTO_SIZE_TEXT_TYPE_NONE)
                gravity = Gravity.CENTER
                setTextColor(foregroundColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(padding, padding, padding, padding)
                view?.addView(this)
            }
        }

        if (drawableDescription.isNotEmpty() && showDrawableDescription) {
            drawableDescriptionTextView = TextView(linearLayout.context).apply {
                text = drawableDescription
                setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    menuSizeManager.getMenuSize().textSizeWithIcon
                )
                setAutoSizeTextTypeWithDefaults(AUTO_SIZE_TEXT_TYPE_NONE)
                setTextColor(foregroundColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER
                setPadding(padding, 0, padding, padding)
                view?.addView(this)
            }
        }

        linearLayout.addView(view)
    }

    fun isVisible(context: Context): Boolean {
        val preferenceManager = PreferenceManager(context)
        return preferenceManager.getMenuItemVisibility(id)
    }

    /**
     * Select the menu item
     */
    fun select() {
        if (!isLinkToMenu && !isMenuHierarchyManipulator && closeOnSelect) {
            MenuManager.getInstance().closeMenuHierarchy()
        }
        action()

        Logger.logEvent("Menu item selected: $id")
    }

    /**
     * Get the location of the menu item on the screen
     * @return The location of the menu item on the screen
     */
    private fun getLocationOnScreen(): IntArray {
        val location = IntArray(2)
        view?.getLocationOnScreen(location)
        return location
    }

    /**
     * Get the x coordinate of the menu item
     * @return The x coordinate of the menu item
     */
    val x: Int
        get() = getLocationOnScreen()[0]

    /**
     * Get the y coordinate of the menu item
     * @return The y coordinate of the menu item
     */
    val y: Int
        get() = getLocationOnScreen()[1]

    /**
     * Get the width of the menu item
     * @return The width of the menu item
     */
    val width: Int
        get() = view?.width ?: 0

    /**
     * Get the height of the menu item
     * @return The height of the menu item
     */
    val height: Int
        get() = view?.height ?: 0
}
