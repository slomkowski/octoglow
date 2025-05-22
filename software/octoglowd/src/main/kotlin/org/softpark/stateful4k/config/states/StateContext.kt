package org.softpark.stateful4k.config.states

internal class StateContext<C, out AS>(
    override val context: C,
    override val state: AS
) :
    IStateContext<C, AS> {}