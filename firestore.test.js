const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const HOST_UID = "host_alice_123";
const PLAYER_BOB_UID = "player_bob_456";
const OUTSIDER_EVE_UID = "outsider_eve_789";
const ROOM_CODE = "RPG777";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function buildValidRoomPayload(now) {
  return {
    roomCode: ROOM_CODE,
    title: "Хроники Эльдхейма: Совместный поход",
    hostUid: HOST_UID,
    hostName: "Алиса",
    memberUids: [HOST_UID],
    worldName: "Хроники Эльдхейма",
    worldGenre: "Тёмное фэнтези",
    worldDescription: "Континент древних руин и угасающих печатей.",
    diceSystem: "d20 + модификатор",
    magicOrTechSystem: "Арканное плетение",
    narrativeTone: "Атмосферное приключение",
    customRules: "",
    useDiceAndStats: true,
    useInventory: true,
    useSpells: true,
    currentRound: 1,
    roundPhase: "COLLECTING",
    hiddenPlotArc: "Культ Пепла готовит ритуал у Шпиля.",
    hiddenNpcKnowledge: "Старый картограф Северин НЕ знает, что среди наёмников есть шпион.",
    hiddenPlotTriggers: "Если герои проверят печать на тубусе — заметят подделку.",
    memorySummary: "",
    createdAt: now,
    updatedAt: now,
  };
}

function buildValidPlayerPayload(uid, heroName, now) {
  return {
    userId: uid,
    roomCode: ROOM_CODE,
    heroName,
    heroArchetype: "Следопыт • Ур. 3",
    heroStats: "ОЗ 28/28 | КД 15",
    pendingAction: "",
    pendingDiceRoll: "",
    submittedRound: 0,
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied all reads and writes on rooms, players, and rounds", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("rooms").doc(ROOM_CODE).get());
  await assertFails(unauthDb.collection("rooms").get());
  await assertFails(unauthDb.collection("rooms").doc(ROOM_CODE).set(buildValidRoomPayload(now)));
});

test("Host room creation, prologue round_0 creation, and @DocumentId extra-key rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).set(buildValidRoomPayload(now))
  );

  await assertSucceeds(
    aliceDb
      .collection("rooms")
      .doc(ROOM_CODE)
      .collection("players")
      .doc(HOST_UID)
      .set(buildValidPlayerPayload(HOST_UID, "Каэль", now))
  );

  await assertSucceeds(
    aliceDb
      .collection("rooms")
      .doc(ROOM_CODE)
      .collection("rounds")
      .doc("round_0")
      .set({
        roomCode: ROOM_CODE,
        roundNumber: 0,
        authorUid: HOST_UID,
        partyActionsSummary: "",
        gmNarrative: "Дождь барабанит по сланцевой крыше таверны «Расколотый Компас»...",
        createdAt: now,
        updatedAt: now,
      })
  );

  // Extra "id" field or ghost field must fail
  await assertFails(
    aliceDb.collection("rooms").doc("BAD123").set({
      ...buildValidRoomPayload(now),
      roomCode: "BAD123",
      id: "BAD123",
    })
  );

  // Tampered future timestamp must fail
  await assertFails(
    aliceDb.collection("rooms").doc("FUT123").set({
      ...buildValidRoomPayload(new Date(Date.now() + 60000)),
      roomCode: "FUT123",
    })
  );
});

test("Second player joins by 6-char code, submits action, cannot spoof another player or host round", async () => {
  const now = new Date(Date.now() - 5000);
  const aliceDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const bobDb = testEnv.authenticatedContext(PLAYER_BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(OUTSIDER_EVE_UID).firestore();

  await assertSucceeds(aliceDb.collection("rooms").doc(ROOM_CODE).set(buildValidRoomPayload(now)));
  await assertSucceeds(
    aliceDb
      .collection("rooms")
      .doc(ROOM_CODE)
      .collection("players")
      .doc(HOST_UID)
      .set(buildValidPlayerPayload(HOST_UID, "Каэль", now))
  );

  // Outsider Eve (not in memberUids) cannot read players or rounds before joining
  await assertFails(
    eveDb.collection("rooms").doc(ROOM_CODE).collection("players").doc(HOST_UID).get()
  );

  // Bob inspects room by code and joins by appending only his UID
  await assertSucceeds(bobDb.collection("rooms").doc(ROOM_CODE).get());
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_CODE).update({
      memberUids: [HOST_UID, PLAYER_BOB_UID],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob creates his player sheet and submits Round 1 action
  await assertSucceeds(
    bobDb
      .collection("rooms")
      .doc(ROOM_CODE)
      .collection("players")
      .doc(PLAYER_BOB_UID)
      .set(buildValidPlayerPayload(PLAYER_BOB_UID, "Лира", new Date(Date.now() - 1000)))
  );

  await assertSucceeds(
    bobDb
      .collection("rooms")
      .doc(ROOM_CODE)
      .collection("players")
      .doc(PLAYER_BOB_UID)
      .update({
        pendingAction: "Проверяю магическую печать на тубусе.",
        pendingDiceRoll: "Бросок d20 + 3 = 18",
        submittedRound: 1,
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Bob CANNOT modify Alice's player sheet
  await assertFails(
    bobDb
      .collection("rooms")
      .doc(ROOM_CODE)
      .collection("players")
      .doc(HOST_UID)
      .update({
        pendingAction: "Взлом чужого хода",
        updatedAt: new Date(Date.now() - 400),
      })
  );

  // Bob CANNOT advance currentRound or write GM round entry (host only)
  await assertFails(
    bobDb.collection("rooms").doc(ROOM_CODE).update({
      currentRound: 2,
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertFails(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("rounds").doc("round_1").set({
      roomCode: ROOM_CODE,
      roundNumber: 1,
      authorUid: PLAYER_BOB_UID,
      partyActionsSummary: "Лира: ...",
      gmNarrative: "Поддельный ответ Мастера",
      createdAt: new Date(Date.now() - 400),
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Host Alice publishes round_1 and increments currentRound to 2
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).collection("rounds").doc("round_1").set({
      roomCode: ROOM_CODE,
      roundNumber: 1,
      authorUid: HOST_UID,
      partyActionsSummary: "[Каэль]: Осматриваю зал\n[Лира]: Проверяю печать [18]",
      gmNarrative: "Лира замечает тонкую трещину в воске арканной печати...",
      createdAt: new Date(Date.now() - 300),
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertSucceeds(
    aliceDb.collection("rooms").doc(ROOM_CODE).update({
      currentRound: 2,
      roundPhase: "COLLECTING",
      hiddenNpcKnowledge: "Лира заметила подделку печати, но Северин пока не знает, что она это поняла.",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Both Alice and Bob can read round_1; Eve cannot
  await assertSucceeds(
    bobDb.collection("rooms").doc(ROOM_CODE).collection("rounds").doc("round_1").get()
  );
  await assertFails(
    eveDb.collection("rooms").doc(ROOM_CODE).collection("rounds").doc("round_1").get()
  );
});

test("Lobby query alignment: memberUids array-contains succeeds, unfiltered rooms list fails", async () => {
  const now = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("rooms").doc(ROOM_CODE).set(buildValidRoomPayload(now));
  });

  const aliceDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const eveDb = testEnv.authenticatedContext(OUTSIDER_EVE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("rooms").where("memberUids", "array-contains", HOST_UID).get()
  );
  await assertSucceeds(
    eveDb.collection("rooms").where("memberUids", "array-contains", OUTSIDER_EVE_UID).get()
  );
  await assertFails(aliceDb.collection("rooms").get());
});
