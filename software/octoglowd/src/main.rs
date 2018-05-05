#[macro_use]
extern crate actix;
extern crate chrono;

use actix::prelude::*;
use chrono::prelude::*;
use message::{ClockDisplayContent, SecondElapsedMessage};
use std::fmt;
use std::thread;
use std::time::Duration;

mod i2c;
mod message;

struct TimerActor {
    recipients: Vec<Recipient<Unsync, SecondElapsedMessage>>
}

impl Actor for TimerActor {
    type Context = Context<Self>;
}

impl TimerActor {
    pub fn new(recipients: &[Recipient<Unsync, SecondElapsedMessage>]) -> Addr<Unsync, TimerActor> {
        let recs = recipients.to_vec();
        TimerActor::create(|ctx| {
            let addr: Addr<Unsync, _> = ctx.address();
            addr.do_send(SecondElapsedMessage {});
            TimerActor { recipients: recs }
        })
    }
}

impl Handler<SecondElapsedMessage> for TimerActor {
    type Result = ();

    fn handle(&mut self, msg: SecondElapsedMessage, ctx: &mut Context<Self>) {
        ctx.run_later(Duration::new(1, 0), move |act, ctx2| {
            let own_addr: Addr<Unsync, _> = ctx2.address();
            own_addr.do_send(SecondElapsedMessage {});

            for recipient in &act.recipients {
                recipient.do_send(SecondElapsedMessage {});
            }
        });
    }
}

struct ClockDisplayActor {
    i2c_actor_addr: Addr<Unsync, i2c::I2CRunner>
}

impl Actor for ClockDisplayActor {
    type Context = Context<Self>;
}

impl Handler<SecondElapsedMessage> for ClockDisplayActor {
    type Result = ();

    fn handle(&mut self, msg: SecondElapsedMessage, ctx: &mut Context<Self>) {
        let now: DateTime<Local> = Local::now();
        let upper_dot = now.second() >= 20;
        let lower_dot = now.second() < 20 || now.second() > 40;
        let cdc = ClockDisplayContent::new(now.hour() as u8, now.minute() as u8, upper_dot, lower_dot);

        self.i2c_actor_addr.do_send(cdc)
    }
}


fn main() {
    let system = System::new("test");

    let i2c_addr: Addr<Unsync, _> = i2c::I2CRunner::new().start();

    let clock_display_addr: Addr<Unsync, _> = ClockDisplayActor { i2c_actor_addr: i2c_addr }.start();

    let timer_addr: Addr<Unsync, _> = TimerActor::new(vec![clock_display_addr.recipient()].as_slice());

    system.run();
}