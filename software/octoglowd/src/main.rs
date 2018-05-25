#[macro_use]
extern crate actix;
extern crate byteorder;
extern crate chrono;
extern crate config;
extern crate futures;
extern crate i2cdev;
extern crate image;
#[macro_use]
extern crate lazy_static;
#[macro_use]
extern crate log;
extern crate simplelog;
#[macro_use]
extern crate diesel;
extern crate dotenv;
extern crate num_traits;

use actix::prelude::*;
use chrono::prelude::*;
use message::*;
use std::io;
use std::sync::RwLock;
use std::time::Duration;

mod schema;
mod timer;
mod i2c;
mod message;
mod database;
mod views;

lazy_static! {
	static ref SETTINGS: RwLock<config::Config> = RwLock::new({
	    let mut s = config::Config::default();
	    s.merge(config::File::with_name("config")).unwrap();
        s.merge(config::Environment::with_prefix("APP")).unwrap();
        s
        });
}

struct ClockDisplayActor;

impl Actor for ClockDisplayActor {
    type Context = Context<Self>;

    fn started(&mut self, ctx: &mut <Self as Actor>::Context) {
        let timer_actor = Arbiter::registry().get::<timer::TimerActor>();
        let addr: Addr<Unsync, ClockDisplayActor> = ctx.address();
        timer_actor.send(message::SubscribeForTimerMessage {
            msg_type: TimerMessageType::EverySecond,
            recipient: addr.recipient(),
        })
            .into_actor(self)
            .then(|_, _, _| { actix::fut::ok(()) })
            .wait(ctx);
    }
}

impl<'a> Handler<TimerMessage> for ClockDisplayActor {
    type Result = ();

    fn handle(&mut self, _: TimerMessage, _: &mut Context<Self>) {
        let now: DateTime<Local> = Local::now();
        let upper_dot = now.second() >= 20;
        let lower_dot = now.second() < 20 || now.second() > 40;
        let cdc = ClockDisplayContent::new(now.hour() as u8, now.minute() as u8, upper_dot, lower_dot);

        let i2c_actor = Arbiter::registry().get::<i2c::I2CActor>();
        i2c_actor.do_send(cdc);
    }
}

fn main() {
    simplelog::CombinedLogger::init(vec![simplelog::TermLogger::new(simplelog::LevelFilter::Info,
                                                                    simplelog::Config::default()).unwrap()]).unwrap();

    let system = System::new("octoglowd");

    let _: () = ClockDisplayActor.start();
    let _: () = views::weather_inside_view::WeatherInsideViewActor::default().start();

    let code = system.run();
    std::process::exit(code);
}