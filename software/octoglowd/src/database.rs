use actix::prelude::*;

pub struct DatabaseActor;

impl Default for DatabaseActor {
    fn default() -> DatabaseActor {
        DatabaseActor {}
    }
}

impl DatabaseActor {}

impl actix::Supervised for DatabaseActor {}

impl Actor for DatabaseActor {
    type Context = Context<Self>;
}

impl ArbiterService for DatabaseActor {}

