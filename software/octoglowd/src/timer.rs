use actix::prelude::*;
use message::{SubscribeForTimerMessage, TimerMessage, TimerMessageType};
use std::collections::HashMap;
use std::time::Duration;

#[derive(Default)]
pub struct TimerActor {
    recipients: HashMap<TimerMessageType, Vec<Recipient<Unsync, TimerMessage>>>
}

impl TimerActor {
    fn schedule_run_later(ctx: &mut Context<TimerActor>, msg_type: TimerMessageType) {
        ctx.run_later(msg_type.duration(), move |act, ctx2| {
            match act.recipients.get(&msg_type) {
                Some(recipients) => {
                    debug!("Sending {:?} message to {} recipients.", msg_type, recipients.len());
                    for recipient in recipients.iter() {
                        recipient.do_send(TimerMessage(msg_type));
                    }
                }
                _ => {}
            }
            TimerActor::schedule_run_later(ctx2, msg_type);
        });
    }
}

impl Actor for TimerActor {
    type Context = Context<Self>;

    fn started(&mut self, ctx: &mut Self::Context) {
        TimerActor::schedule_run_later(ctx, TimerMessageType::UserInteractionPool);
        TimerActor::schedule_run_later(ctx, TimerMessageType::EveryMinute);
        TimerActor::schedule_run_later(ctx, TimerMessageType::EverySecond);
    }
}

impl ArbiterService for TimerActor {}

impl Supervised for TimerActor {}

impl Handler<SubscribeForTimerMessage> for TimerActor {
    type Result = ();

    fn handle(&mut self, subscription: SubscribeForTimerMessage, _: &mut Context<Self>) {
        let mut recipients = self.recipients.entry(subscription.msg_type).or_insert(Vec::new());
        recipients.push(subscription.recipient);
    }
}
