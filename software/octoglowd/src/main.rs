#[macro_use]
extern crate actix;
extern crate byteorder;
extern crate chrono;
extern crate futures;
extern crate i2cdev;
extern crate image;
#[macro_use]
extern crate log;
extern crate simplelog;

use simplelog::*;
use actix::prelude::*;
use chrono::prelude::*;
use message::*;
use std::time::Duration;
use std::io;

mod i2c;
mod message;
mod database;

#[derive(Default)]
struct TimerActor {
    recipients: Vec<Recipient<Unsync, EverySecondMessage>>
}

impl Actor for TimerActor {
    type Context = Context<Self>;

    fn started(&mut self, ctx: &mut <Self as Actor>::Context) {
        let own_addr: Addr<Unsync, _> = ctx.address();
        own_addr.do_send(EverySecondMessage {});
    }
}

impl ArbiterService for TimerActor {}

impl Supervised for TimerActor {}

impl Handler<SubscribeForEverySecondMessage> for TimerActor {
    type Result = ();

    fn handle(&mut self, msg: SubscribeForEverySecondMessage, _: &mut Context<Self>) {
        self.recipients.push(msg.recipient);
    }
}

impl Handler<EverySecondMessage> for TimerActor {
    type Result = ();

    fn handle(&mut self, _: EverySecondMessage, ctx: &mut Context<Self>) {
        ctx.run_later(Duration::new(1, 0), move |act, ctx2| {
            let own_addr: Addr<Unsync, _> = ctx2.address();
            own_addr.do_send(EverySecondMessage {});

            for recipient in &act.recipients {
                recipient.do_send(EverySecondMessage {});
            }
        });
    }
}

struct ClockDisplayActor;

impl Actor for ClockDisplayActor {
    type Context = Context<Self>;

    fn started(&mut self, ctx: &mut <Self as Actor>::Context) {
        let timer_actor = Arbiter::registry().get::<TimerActor>();
        let addr: Addr<Unsync, ClockDisplayActor> = ctx.address();
        timer_actor.send(message::SubscribeForEverySecondMessage { recipient: addr.recipient() })
            .into_actor(self)
            .then(|_, _, _| { actix::fut::ok(()) })
            .wait(ctx);
    }
}

impl<'a> Handler<EverySecondMessage> for ClockDisplayActor {
    type Result = ();

    fn handle(&mut self, _: EverySecondMessage, _: &mut Context<Self>) {
        let now: DateTime<Local> = Local::now();
        let upper_dot = now.second() >= 20;
        let lower_dot = now.second() < 20 || now.second() > 40;
        let cdc = ClockDisplayContent::new(now.hour() as u8, now.minute() as u8, upper_dot, lower_dot);

        let i2c_actor = Arbiter::registry().get::<i2c::I2CActor>();
        i2c_actor.do_send(cdc);
    }
}

fn main() {
    CombinedLogger::init(vec![TermLogger::new(LevelFilter::Info, Config::default()).unwrap()]).unwrap();

    let system = System::new("octoglowd");

    let _: () = ClockDisplayActor.start();

    let code = system.run();
    std::process::exit(code);
}