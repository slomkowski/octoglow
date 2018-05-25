use actix::prelude::*;
use database;
use i2c;
use message::*;
use timer;

pub struct WeatherInsideViewActor {
    latest_report: Option<InsideWeatherSensorReport>
}

impl Default for WeatherInsideViewActor {
    fn default() -> Self {
        WeatherInsideViewActor {
            latest_report: None
        }
    }
}

impl WeatherInsideViewActor {
    fn format_temperature(t: f32) -> String {
        format!("{:>+width$.prec$}\u{00B0}C", t, width = 5, prec = 1)
    }

    fn format_humidity(h: f32) -> String {
        format!("H:{value:>align$.prec$}%", align = 3, prec = 0, value = h)
    }

    fn format_pressure(p: f32) -> String {
        format!("{:>6.1}hPa", p)
    }

    fn draw_view_on_screen(&self, mode: &DrawViewOnScreenType) {
        let i2c_actor = Arbiter::registry().get::<i2c::I2CActor>();

        if *mode == DrawViewOnScreenType::First {
            i2c_actor.do_send(FrontDisplayClear);
            match &self.latest_report {
                &Some(ref rep) => {
                    i2c_actor.do_send(FrontDisplayStaticText::new(20, &WeatherInsideViewActor::format_temperature(rep.temperature)));
                    i2c_actor.do_send(FrontDisplayStaticText::new(0, &WeatherInsideViewActor::format_humidity(rep.humidity)));
                    i2c_actor.do_send(FrontDisplayStaticText::new(11, &WeatherInsideViewActor::format_pressure(rep.pressure)));
                }
                &None => {
                    i2c_actor.do_send(FrontDisplayStaticText::new(20, "---.-\u{00B0}C"));
                    i2c_actor.do_send(FrontDisplayStaticText::new(0, "H:---%"));
                    i2c_actor.do_send(FrontDisplayStaticText::new(11, "----.-hPa"));
                }
            }
        }
    }
}

impl Actor for WeatherInsideViewActor {
    type Context = Context<Self>;

    fn started(&mut self, ctx: &mut <Self as Actor>::Context) {
        let timer_actor = Arbiter::registry().get::<timer::TimerActor>();
        let addr: Addr<Unsync, WeatherInsideViewActor> = ctx.address();

        //todo wysłać w kolejności

        timer_actor.do_send(SubscribeForTimerMessage {
            msg_type: TimerMessageType::EverySecond,
            recipient: addr.clone().recipient(),
        });

        timer_actor.send(SubscribeForTimerMessage {
            msg_type: TimerMessageType::EveryMinute,
            recipient: addr.recipient(),
        })
            .into_actor(self)
            .then(|_, _, _| {
                actix::fut::ok(())
            })
            .wait(ctx);
    }
}

impl ArbiterService for WeatherInsideViewActor {}

impl Supervised for WeatherInsideViewActor {}

impl Handler<TimerMessage> for WeatherInsideViewActor {
    type Result = ();

    fn handle(&mut self, msg: TimerMessage, ctx: &mut Context<Self>) {
        //assert_eq!(msg.0, TimerMessageType::EveryMinute);

        if msg.0 == TimerMessageType::EverySecond {
            self.draw_view_on_screen(&DrawViewOnScreenType::First);
            return;
        }

        let i2c_actor = Arbiter::registry().get::<i2c::I2CActor>();
        i2c_actor.send(GetInsideWeatherReport)
            .into_actor(self)
            .then(|report, act, ctx2| {
                let db = Arbiter::registry().get::<database::DatabaseActor>();

                let rep = report.unwrap().unwrap();
                act.latest_report = Some(rep.clone());
                db.do_send(rep.clone());

                actix::fut::ok(())
            })
            .wait(ctx);

        //todo przenieść do aktora od outside
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

impl Handler<DrawViewOnScreen> for WeatherInsideViewActor {
    type Result = ();

    fn handle(&mut self, msg: DrawViewOnScreen, ctx: &mut Context<Self>) {
        self.draw_view_on_screen(&msg.0);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_format_humidity() {
        assert_eq!("H:100%", WeatherInsideViewActor::format_humidity(100.0));
        assert_eq!("H:  0%", WeatherInsideViewActor::format_humidity(0.0));
        assert_eq!("H: 24%", WeatherInsideViewActor::format_humidity(24.1001));
        assert_eq!("H: 39%", WeatherInsideViewActor::format_humidity(38.8903));
    }

    #[test]
    fn test_format_temperature() {
        assert_eq!("+24.4\u{00B0}C", WeatherInsideViewActor::format_temperature(24.434));
        assert_eq!("-12.0\u{00B0}C", WeatherInsideViewActor::format_temperature(-12.0));
        assert_eq!(" +3.2\u{00B0}C", WeatherInsideViewActor::format_temperature(3.21343));
        assert_eq!("-12.7\u{00B0}C", WeatherInsideViewActor::format_temperature(-12.693423));
        assert_eq!(" +0.0\u{00B0}C", WeatherInsideViewActor::format_temperature(0.0));
    }

    #[test]
    fn test_format_pressure() {
        assert_eq!("1001.2hPa", WeatherInsideViewActor::format_pressure(1001.2133));
        assert_eq!(" 981.4hPa", WeatherInsideViewActor::format_pressure(981.3990));
        assert_eq!("1024.0hPa", WeatherInsideViewActor::format_pressure(1024.02));
    }
}