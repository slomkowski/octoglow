table! {
    inside_weather_report (id) {
        id -> Nullable<BigInt>,
        timestamp -> Timestamp,
        temperature -> Float,
        humidity -> Float,
        pressure -> Float,
    }
}

table! {
    outside_weather_report (id) {
        id -> Nullable<BigInt>,
        timestamp -> Timestamp,
        temperature -> Float,
        humidity -> Float,
        weak_battery -> Bool,
    }
}

allow_tables_to_appear_in_same_query!(
    inside_weather_report,
    outside_weather_report,
);
