package io.skygear.plugins.chat.ui.holder

import android.view.View
import android.widget.LinearLayout
import com.stfalcon.chatkit.utils.ShapeImageView
import io.skygear.plugins.chat.ui.model.Message


class SenderAvatarMessageView(itemView: View){

    var avatarView: LinearLayout? = null

    init {
        avatarView = itemView.findViewById<LinearLayout>(io.skygear.plugins.chat.R.id.userAvatar)
    }

    fun onBind(message: Message) {
        avatarView?.visibility = if (message.style.showSender) View.VISIBLE else View.GONE
    }
}
