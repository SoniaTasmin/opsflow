/**
 * Seeds the demo dataset through the application's own HTTP API under a real Keycloak token, so
 * the data is by construction data the application would accept. Deterministic on every run.
 *
 * Local only: it authenticates with the realm's development CLI client, which no deployed
 * configuration has.
 */

const API = process.env.OPSFLOW_API_URL ?? "http://localhost:8080";
const ISSUER =
    process.env.OPSFLOW_OIDC_ISSUER_URI ??
    "http://localhost:8081/realms/opsflow";
const PASSWORD = process.env.OPSFLOW_DEMO_PASSWORD ?? "password";

const ORGANIZATIONS = [
    { code: "BERLIN_OPS", name: "Berlin Operations" },
    { code: "LISBON_OPS", name: "Lisbon Operations" },
    { code: "NAIROBI_OPS", name: "Nairobi Operations" },
    { code: "SINGAPORE_OPS", name: "Singapore Operations" },
    { code: "TORONTO_OPS", name: "Toronto Operations" },
    { code: "OSLO_OPS", name: "Oslo Operations" },
];

const METRICS = [
    {
        code: "HEADCOUNT",
        name: "Headcount",
        description: "People employed at the end of the period.",
        unit: "people",
        valueType: "INTEGER",
        minValue: 0,
        maxValue: 100000,
        required: true,
    },
    {
        code: "OPEN_TICKETS",
        name: "Open support tickets",
        description: "Tickets still open when the period closed.",
        unit: "tickets",
        valueType: "INTEGER",
        minValue: 0,
        maxValue: 1000000,
        required: true,
    },
    {
        code: "REVENUE",
        name: "Revenue",
        description: "Recognised revenue for the period.",
        unit: "EUR",
        valueType: "DECIMAL",
        minValue: 0,
        required: true,
    },
    {
        code: "SLA_COMPLIANCE",
        name: "SLA compliance",
        description:
            "Share of tickets answered within the agreed time. Bounded by its type.",
        unit: "%",
        valueType: "PERCENTAGE",
        required: false,
    },
    {
        code: "AVG_HANDLING_TIME",
        name: "Average handling time",
        description: "Mean minutes to resolve a ticket.",
        unit: "minutes",
        valueType: "DECIMAL",
        minValue: 0,
        maxValue: 2880,
        required: false,
    },
];

const VALUES = {
    BERLIN_OPS: {
        HEADCOUNT: 142,
        OPEN_TICKETS: 37,
        REVENUE: 1284500.5,
        SLA_COMPLIANCE: 96.4,
    },
    LISBON_OPS: {
        HEADCOUNT: 88,
        OPEN_TICKETS: 61,
        REVENUE: 612300,
        SLA_COMPLIANCE: 88.1,
    },
    NAIROBI_OPS: {
        HEADCOUNT: 54,
        OPEN_TICKETS: 22,
        REVENUE: 298750.25,
        SLA_COMPLIANCE: 91,
    },
    SINGAPORE_OPS: {
        HEADCOUNT: 121,
        OPEN_TICKETS: 44,
        REVENUE: 1043900,
        SLA_COMPLIANCE: 93.7,
    },
    TORONTO_OPS: { HEADCOUNT: 76, OPEN_TICKETS: 18 },
};

async function token(username) {
    const response = await fetch(`${ISSUER}/protocol/openid-connect/token`, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
            client_id: "opsflow-dev-cli",
            grant_type: "password",
            username,
            password: PASSWORD,
        }),
    });
    if (!response.ok) {
        throw new Error(
            `Could not sign in as ${username}: ${response.status} ${await response.text()}`,
        );
    }
    return (await response.json()).access_token;
}

async function api(accessToken, method, path, body) {
    const response = await fetch(`${API}${path}`, {
        method,
        headers: {
            Authorization: `Bearer ${accessToken}`,
            ...(body === undefined
                ? {}
                : { "Content-Type": "application/json" }),
        },
        body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await response.text();
    if (!response.ok) {
        throw new Error(`${method} ${path} -> ${response.status} ${text}`);
    }
    if (text.length === 0) {
        return null;
    }
    try {
        return JSON.parse(text);
    } catch (cause) {
        throw new Error(
            `${method} ${path} -> ${response.status} returned no JSON: ${text.slice(0, 200)}`,
        );
    }
}

async function alreadySeeded(admin) {
    const page = await api(
        admin,
        "GET",
        "/api/v1/organizations?q=BERLIN_OPS&size=1",
    );
    return page.content.length > 0;
}

async function createOrganizations(admin) {
    const byCode = {};
    for (const organization of ORGANIZATIONS) {
        byCode[organization.code] = await api(
            admin,
            "POST",
            "/api/v1/organizations",
            organization,
        );
    }
    return byCode;
}

async function createMetrics(admin) {
    const byCode = {};
    for (const metric of METRICS) {
        const { required, ...definition } = metric;
        byCode[metric.code] = await api(
            admin,
            "POST",
            "/api/v1/metrics",
            definition,
        );
    }
    return byCode;
}

async function addMembers(admin, organizations, usernames) {
    for (const username of usernames.memberships ?? []) {
        // Nothing to add until a person has signed in once: the local user is created on first call.
        const users = await api(
            admin,
            "GET",
            `/api/v1/users?q=${username.user}&size=5`,
        );
        const user = users.content.find(
            (candidate) => candidate.username === username.user,
        );
        if (!user) {
            throw new Error(
                `No local user ${username.user}; they must sign in once before being added`,
            );
        }
        for (const code of username.organizations) {
            await api(
                admin,
                "PUT",
                `/api/v1/organizations/${organizations[code].id}/members/${user.id}`,
            );
        }
    }
}

async function createPeriod(
    admin,
    { code, startsOn, endsOn, submissionDeadline, metrics, participants, open },
) {
    let period = await api(admin, "POST", "/api/v1/periods", {
        code,
        periodType: "QUARTER",
        startsOn,
        endsOn,
        submissionDeadline,
    });

    period = await api(admin, "PUT", `/api/v1/periods/${period.id}/metrics`, {
        metrics: metrics.map((metric, index) => ({
            metricId: metric.id,
            required: metric.required,
            displayOrder: index,
        })),
        version: period.version,
    });

    period = await api(
        admin,
        "PUT",
        `/api/v1/periods/${period.id}/organizations`,
        {
            organizationIds: participants.map(
                (organization) => organization.id,
            ),
            version: period.version,
        },
    );

    if (open) {
        period = await api(admin, "POST", `/api/v1/periods/${period.id}/open`, {
            version: period.version,
        });
    }
    return period;
}

/** Drives a submission through the real workflow endpoints, echoing the version each step returns. */
async function submission(
    authorToken,
    organizationId,
    reportingPeriodId,
    values,
    steps,
    reviewerToken,
) {
    let current = await api(authorToken, "POST", "/api/v1/submissions", {
        organizationId,
        reportingPeriodId,
    });

    if (Object.keys(values).length > 0) {
        current = await api(
            authorToken,
            "PUT",
            `/api/v1/submissions/${current.id}/values`,
            {
                values: Object.entries(values).map(([metricId, value]) => ({
                    metricId,
                    value,
                })),
                version: current.version,
            },
        );
    }

    for (const step of steps) {
        const actor = ["claim", "approve", "reject", "release"].includes(
            step.command,
        )
            ? reviewerToken
            : authorToken;
        const body =
            step.command === "reject"
                ? { reason: step.reason, version: current.version }
                : { version: current.version };
        current = await api(
            actor,
            "POST",
            `/api/v1/submissions/${current.id}/${step.command}`,
            body,
        );
    }
    return current;
}

function valuesFor(code, metrics) {
    return Object.fromEntries(
        Object.entries(VALUES[code] ?? {}).map(([metricCode, value]) => [
            metrics[metricCode].id,
            value,
        ]),
    );
}

async function main() {
    const [admin, reporter, reviewer] = await Promise.all([
        token("admin"),
        token("reporter"),
        token("reviewer"),
    ]);

    // Signing in is what creates the local user, and membership needs one to point at.
    await Promise.all([
        api(admin, "GET", "/api/v1/me"),
        api(reporter, "GET", "/api/v1/me"),
        api(reviewer, "GET", "/api/v1/me"),
    ]);

    if (await alreadySeeded(admin)) {
        console.error(
            "Demo data is already present. Run `make demo-reset` to start from empty.",
        );
        process.exit(2);
    }

    const organizations = await createOrganizations(admin);
    const metrics = await createMetrics(admin);
    await addMembers(admin, organizations, {
        memberships: [
            { user: "reporter", organizations: ["BERLIN_OPS", "LISBON_OPS"] },
        ],
    });

    const scope = METRICS.map((metric) => ({
        id: metrics[metric.code].id,
        required: metric.required,
    }));
    const participants = ORGANIZATIONS.map(
        (organization) => organizations[organization.code],
    );

    const closed = await createPeriod(admin, {
        code: "2025-Q4",
        startsOn: "2025-10-01",
        endsOn: "2025-12-31",
        submissionDeadline: "2026-01-15",
        metrics: scope,
        participants: [organizations.BERLIN_OPS, organizations.LISBON_OPS],
        open: true,
    });
    for (const code of ["BERLIN_OPS", "LISBON_OPS"]) {
        await submission(
            reporter,
            organizations[code].id,
            closed.id,
            valuesFor(code, metrics),
            [
                { command: "submit" },
                { command: "claim" },
                { command: "approve" },
            ],
            reviewer,
        );
    }
    await api(admin, "POST", `/api/v1/periods/${closed.id}/close`, {
        version: (await api(admin, "GET", `/api/v1/periods/${closed.id}`))
            .period.version,
    });

    const open = await createPeriod(admin, {
        code: "2026-Q1",
        startsOn: "2026-01-01",
        endsOn: "2026-03-31",
        submissionDeadline: "2026-04-15",
        metrics: scope,
        participants,
        open: true,
    });

    // One organization per summary bucket, so the summary screen shows all six at once.
    await submission(
        reporter,
        organizations.BERLIN_OPS.id,
        open.id,
        valuesFor("BERLIN_OPS", metrics),
        [{ command: "submit" }, { command: "claim" }, { command: "approve" }],
        reviewer,
    );
    await submission(
        reporter,
        organizations.LISBON_OPS.id,
        open.id,
        valuesFor("LISBON_OPS", metrics),
        [
            { command: "submit" },
            { command: "claim" },
            {
                command: "reject",
                reason: "Open tickets look too high against last quarter. Please confirm.",
            },
        ],
        reviewer,
    );
    await submission(
        admin,
        organizations.NAIROBI_OPS.id,
        open.id,
        valuesFor("NAIROBI_OPS", metrics),
        [{ command: "submit" }, { command: "claim" }],
        reviewer,
    );
    await submission(
        admin,
        organizations.SINGAPORE_OPS.id,
        open.id,
        valuesFor("SINGAPORE_OPS", metrics),
        [{ command: "submit" }],
        reviewer,
    );
    await submission(
        admin,
        organizations.TORONTO_OPS.id,
        open.id,
        valuesFor("TORONTO_OPS", metrics),
        [],
        reviewer,
    );
    // OSLO_OPS is left alone: a participant that has not started.

    await createPeriod(admin, {
        code: "2026-Q2",
        startsOn: "2026-04-01",
        endsOn: "2026-06-30",
        submissionDeadline: "2026-07-15",
        metrics: scope,
        participants,
        open: false,
    });

    // Created after the period opened, so it is deliberately absent from its frozen scope.
    await api(admin, "POST", "/api/v1/organizations", {
        code: "ZURICH_OPS",
        name: "Zurich Operations (opened after 2026-Q1 froze)",
    });

    console.log(
        `Seeded ${ORGANIZATIONS.length + 1} organizations, ${METRICS.length} metrics and 3 periods.`,
    );
    console.log(
        "Sign in at http://localhost:4200 as admin, reporter or reviewer (password: password).",
    );
}

main().catch((error) => {
    console.error(error.message);
    process.exit(1);
});
