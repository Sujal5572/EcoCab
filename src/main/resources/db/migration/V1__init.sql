CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";

-- ShedLock table (required for @SchedulerLock)
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

CREATE TABLE IF NOT EXISTS users (
    id           UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone_number VARCHAR(15) NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS corridors (
    id             UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    name           VARCHAR(200) NOT NULL,
    code           VARCHAR(50)  NOT NULL UNIQUE,
    start_point    GEOGRAPHY(POINT,4326),
    end_point      GEOGRAPHY(POINT,4326),
    total_segments INT         NOT NULL,
    is_active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS corridor_segments (
    id                  UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    corridor_id         UUID         NOT NULL REFERENCES corridors(id) ON DELETE CASCADE,
    segment_index       INT          NOT NULL,
    segment_start       GEOGRAPHY(POINT,4326),
    segment_end         GEOGRAPHY(POINT,4326),
    length_km           NUMERIC(6,3) NOT NULL DEFAULT 0,
    max_driver_capacity INT          NOT NULL DEFAULT 5,
    UNIQUE (corridor_id, segment_index)
);

CREATE TABLE IF NOT EXISTS drivers (
    id                    UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone_number          VARCHAR(15) NOT NULL UNIQUE,
    name                  VARCHAR(100) NOT NULL,
    vehicle_number        VARCHAR(20) NOT NULL UNIQUE,
    vehicle_type          VARCHAR(20) NOT NULL DEFAULT 'AUTO',
    status                VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    current_corridor_id   UUID        REFERENCES corridors(id),
    current_segment_index INT,
    last_known_location   GEOGRAPHY(POINT,4326),
    location_updated_at   TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS demand_signals (
    id            UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id       UUID        NOT NULL REFERENCES users(id),
    corridor_id   UUID        NOT NULL REFERENCES corridors(id),
    segment_index INT         NOT NULL,
    user_location GEOGRAPHY(POINT,4326),
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '30 minutes'),
    picked_up_at  TIMESTAMPTZ
);

-- Prevents duplicate demand signals from retried API calls
CREATE UNIQUE INDEX IF NOT EXISTS idx_demand_one_active_per_user_corridor
    ON demand_signals(user_id, corridor_id)
    WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS demand_aggregates (
    id                  UUID        DEFAULT uuid_generate_v4(),
    corridor_id         UUID        NOT NULL REFERENCES corridors(id),
    segment_index       INT         NOT NULL,
    active_demand_count INT         NOT NULL DEFAULT 0,
    drivers_in_segment  INT         NOT NULL DEFAULT 0,
    window_start        TIMESTAMPTZ NOT NULL,
    window_end          TIMESTAMPTZ NOT NULL,
    computed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, window_start)
);

CREATE TABLE IF NOT EXISTS trips (
    id                   UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    driver_id            UUID        NOT NULL REFERENCES drivers(id),
    corridor_id          UUID        NOT NULL REFERENCES corridors(id),
    start_segment_index  INT         NOT NULL DEFAULT 0,
    end_segment_index    INT,
    status               VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    passengers_picked_up INT         NOT NULL DEFAULT 0,
    started_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at             TIMESTAMPTZ
);

-- Seed data for local testing
INSERT INTO corridors (id, name, code, total_segments, is_active)
VALUES ('a0000000-0000-0000-0000-000000000001',
        'Gai Ghat → Gandhi Maidan', 'GG_GM', 8, TRUE)
ON CONFLICT (code) DO NOTHING;