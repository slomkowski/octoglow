package org.softpark.stateful4k.config.events

internal class EventContext<C, out AS, out AE>(
    override val context: C,
    override val state: AS,
    override val event: AE
) :
    IEventContext<C, AS, AE> {}