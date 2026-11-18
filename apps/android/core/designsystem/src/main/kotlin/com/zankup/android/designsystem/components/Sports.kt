package com.zankup.android.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.zankup.android.designsystem.theme.ZankupTheme

/** The families of sports the interface colours and draws differently. */
enum class SportFamily(val icon: ZIcon) {
    RUN(ZIcon.Run),
    RIDE(ZIcon.Ride),
    WALK(ZIcon.Walk),
    STRENGTH(ZIcon.Strength),
    ROW(ZIcon.Row),
    SWIM(ZIcon.Swim),
    OTHER(ZIcon.Other)
}

val SportFamily.color: Color
    @Composable @ReadOnlyComposable
    get() =
        ZankupTheme.colors.sport.let {
            when (this) {
                SportFamily.RUN -> it.run
                SportFamily.RIDE -> it.ride
                SportFamily.WALK -> it.walk
                SportFamily.STRENGTH -> it.strength
                SportFamily.ROW -> it.row
                SportFamily.SWIM -> it.swim
                SportFamily.OTHER -> it.other
            }
        }
