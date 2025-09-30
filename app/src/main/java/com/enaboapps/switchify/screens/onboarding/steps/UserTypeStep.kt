package com.enaboapps.switchify.screens.onboarding.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.screens.onboarding.UserType
import com.enaboapps.switchify.theme.Dimens

@Composable
fun UserTypeStep(
    onUserTypeSelected: (UserType) -> Unit
) {
    var selectedType by remember { mutableStateOf<UserType?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spaceL),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(Dimens.spaceL + Dimens.spaceM))

        Text(
            text = stringResource(R.string.onboarding_why_here_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = Dimens.spaceXs)
        )

        Text(
            text = stringResource(R.string.onboarding_why_here_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimens.spaceL + Dimens.spaceS)
        )

        // User type selection using Picker
        val userTypes = UserType.entries.toList()
        val userTypeUser = stringResource(R.string.onboarding_user_type_user)
        val userTypeSpecialist = stringResource(R.string.onboarding_user_type_specialist)
        val userTypeCarer = stringResource(R.string.onboarding_user_type_carer)
        val userTypeOther = stringResource(R.string.onboarding_user_type_other)
        val userDescUser = stringResource(R.string.onboarding_user_type_user_desc)
        val userDescSpecialist = stringResource(R.string.onboarding_user_type_specialist_desc)
        val userDescCarer = stringResource(R.string.onboarding_user_type_carer_desc)
        val userDescOther = stringResource(R.string.onboarding_user_type_other_desc)

        Picker(
            titleResId = R.string.onboarding_why_here_title,
            selectedItem = selectedType,
            items = userTypes,
            onItemSelected = { userType ->
                selectedType = userType
            },
            itemToString = { userType ->
                when (userType) {
                    UserType.USER -> userTypeUser
                    UserType.SPECIALIST -> userTypeSpecialist
                    UserType.CARER_FAMILY -> userTypeCarer
                    UserType.OTHER -> userTypeOther
                }
            },
            itemDescription = { userType ->
                when (userType) {
                    UserType.USER -> userDescUser
                    UserType.SPECIALIST -> userDescSpecialist
                    UserType.CARER_FAMILY -> userDescCarer
                    UserType.OTHER -> userDescOther
                }
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        // Continue button
        ActionButton(
            textResId = R.string.onboarding_continue,
            enabled = selectedType != null,
            onClick = {
                selectedType?.let { onUserTypeSelected(it) }
            }
        )
    }
}
