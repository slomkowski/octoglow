#[macro_use]
extern crate actix;
extern crate futures;
extern crate chrono;
extern crate image;
extern crate byteorder;
extern crate i2cdev;

use futures::{future, Future};
use actix::*;
use chrono::prelude::*;
use message::*;
use std::fmt;
use std::thread;
use std::time::Duration;

mod i2c;
mod message;
mod database;

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

struct ClockDisplayActor;

impl Actor for ClockDisplayActor {
    type Context = Context<Self>;
}

impl<'a> Handler<SecondElapsedMessage> for ClockDisplayActor {
    type Result = ();

    fn handle(&mut self, msg: SecondElapsedMessage, ctx: &mut Context<Self>) {
        let now: DateTime<Local> = Local::now();
        let upper_dot = now.second() >= 20;
        let lower_dot = now.second() < 20 || now.second() > 40;
        let cdc = ClockDisplayContent::new(now.hour() as u8, now.minute() as u8, upper_dot, lower_dot);

        let i2c_actor = Arbiter::registry().get::<i2c::I2CRunner>();
        i2c_actor.do_send(cdc);

        i2c_actor.do_send(message::GetInsideWeatherReport {});

        i2c_actor.do_send(message::FrontDisplayGetButtonState {});
    }
}

fn main() {
    let system = System::new("octoglowd");

    let i2c_addr: Addr<Unsync, _> = i2c::I2CRunner::new().start();

    let clock_display_addr: Addr<Unsync, _> = ClockDisplayActor {}.start();

    let timer_addr: Addr<Unsync, _> = TimerActor::new(vec![clock_display_addr.recipient()].as_slice());

    i2c_addr.do_send(message::FrontDisplayClear);
    i2c_addr.do_send(message::FrontDisplayStaticText::new(5, "mądry pies"));
    i2c_addr.do_send(message::FrontDisplayStaticText::new(1, "ąęłść mieć"));

    i2c_addr.do_send(message::FrontDisplayScrollingText::new(ScrollingTextSlot::SLOT1, 20, 5, "Misje transportowe, sprzątające, elektryczne, elektryczne."));

    i2c_addr.do_send(message::FrontDisplayClear);

    let img = image::open("/home/michal/x.png").unwrap();
    i2c_addr.do_send(message::FrontDisplayGraphics::new(5 * 35, false, img.grayscale().as_luma8().unwrap(), true));

    let img2 = image::open("/home/michal/y.png").unwrap();
    i2c_addr.do_send(message::FrontDisplayGraphics::new(5 * 2, false, img2.grayscale().as_luma8().unwrap(), true));
    i2c_addr.do_send(message::FrontDisplayUpperBar::new([true, true, false, true, false, false, false, false, false, false, false, false, true, false, false, false, false, false, false, true]));

    i2c_addr.do_send(message::FrontDisplayUpperBar::enabled_positions(&[0, 1, 19]));

    let r = i2c_addr.send(message::FrontDisplayGetButtonState {});

    system.handle().spawn(r.then(|report| {
        println!("Button state: {:?}", report.unwrap());
        future::result(Ok(()))
    }));

    let code = system.run();
    std::process::exit(code);
}