package com.jing.ddys.update

object UpdateResumePolicy {
    fun shouldResumeInstall(state: UpdateState): Boolean {
        return state is UpdateState.Ready && state.permissionRequired
    }
}
