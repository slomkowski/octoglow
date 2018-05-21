use actix::prelude::*;
use database;
use i2c;
use message::*;
use timer;

#[derive(Default)]
pub struct WeatherInsideViewActor;

impl Actor for WeatherInsideViewActor {
    type Context = Context<Self>;

    fn started(&mut self, ctx: &mut <Self as Actor>::Context) {
        let timer_actor = Arbiter::registry().get::<timer::TimerActor>();
        let addr: Addr<Unsync, WeatherInsideViewActor> = ctx.address();
        timer_actor.send(SubscribeForTimerMessage {
            msg_type: TimerMessageType::EveryMinute,
            recipient: addr.recipient(),
        })
            .into_actor(self)
            .then(|_, _, _| { actix::fut::ok(()) })
            .wait(ctx);
    }
}

impl ArbiterService for WeatherInsideViewActor {}

impl Supervised for WeatherInsideViewActor {}

impl Handler<TimerMessage> for WeatherInsideViewActor {
    type Result = ();

    fn handle(&mut self, msg: TimerMessage, ctx: &mut Context<Self>) {
        assert_eq!(msg.0, TimerMessageType::EveryMinute);

        let i2c_actor = Arbiter::registry().get::<i2c::I2CActor>();
        i2c_actor.send(GetInsideWeatherReport)
            .into_actor(self)
            .then(|report, act, ctx2| {
                let db = Arbiter::registry().get::<database::DatabaseActor>();

                db.do_send(report.unwrap().unwrap());

                //todo create current report
                actix::fut::ok(())
            })
            .wait(ctx);

        i2c_actor.send(ClockDisplayGetWeatherReport)
            .into_actor(self)
            .then(|report, act, ctx2| {
                let db = Arbiter::registry().get::<database::DatabaseActor>();

                db.do_send(report.unwrap().unwrap());

                //todo create current report
                actix::fut::ok(())
            })
            .wait(ctx);
    }
}