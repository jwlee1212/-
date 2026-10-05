package baseballgm.market

import baseballgm.io.BalanceConfig
import baseballgm.model.Attribute
import baseballgm.model.Batter
import baseballgm.model.BatterRatings
import baseballgm.model.Condition
import baseballgm.model.Contract
import baseballgm.model.ContractType
import baseballgm.model.GrowthType
import baseballgm.model.Hand
import baseballgm.model.HiddenTraits
import baseballgm.model.MilitaryStatus
import baseballgm.model.Origin
import baseballgm.model.Pitcher
import baseballgm.model.PitcherRatings
import baseballgm.model.PitcherRole
import baseballgm.model.PlayerId
import baseballgm.model.Position
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId



/** M6 테스트가 공유하는 설정과 선수 생성기. */
internal val DRAFT_BALANCE: BalanceConfig = BalanceConfig.parse(
    """
    {
      "ratingScale": { "starterAverage": 60 },
      "positionNeed": { "critical": [1.3, 1.5], "noBackup": 1.1, "adequate": 1.0, "surplus": 0.8 },
      "teamStrength": {
        "compositeWeights": { "lineup": 0.45, "rotation": 0.35, "bullpen": 0.20 },
        "ratingToStrength": { "pivot": 52.0, "slope": 2.0 },
        "lineupWeights": [1.15, 1.15, 1.10, 1.10, 1.05, 0.95, 0.90, 0.85, 0.75],
        "rotationWeights": [1.30, 1.15, 1.00, 0.85, 0.70],
        "bullpenWeights": [1.40, 1.20, 1.10, 1.00, 0.90, 0.75, 0.65],
        "targetTolerance": 2.0
      },
      "playerOverallWeights": {
        "batter": { "contact": 0.28, "power": 0.28, "eye": 0.18, "speed": 0.11, "defense": 0.15 },
        "pitcher": { "stuff": 0.40, "control": 0.30, "groundball": 0.15, "stamina": 0.15 }
      },
      "draft": {
        "rounds": 3,
        "poolSize": 60,
        "tradeablePickYears": 2,
        "noConsecutiveFirstRoundTrade": true,
        "signingBonus": { "firstPick": 5.0, "lastPick": 0.1, "curve": 1.9 },
        "aiBias": { "highSchool": [0.88, 1.12], "pitcher": [0.90, 1.10], "readiness": [0.15, 0.55] },
        "starterPremium": 1.0,
        "valueWeights": { "current": 0.35, "potential": 0.65 }
      },
      "draftPickValue": {
        "byPick": { "1": 1.0, "10": 0.55, "20": 0.35, "40": 0.18 },
        "futurePickDiscount": 0.85,
        "firstPickSurplus": 18.0,
        "modeMultiplier": { "contend": 0.7, "neutral": 1.0, "rebuild": 1.35 }
      },
      "valuation": {
        "horizonYears": 5,
        "salaryPerWar": 3.0,
        "warByOverall": {
          "batter": { "40": 0.0, "45": 0.0, "50": 0.05, "55": 0.7, "60": 1.8, "65": 2.6, "70": 3.8, "75": 5.0, "80": 6.2 },
          "starter": { "40": 0.0, "45": 0.05, "50": 0.4, "55": 1.2, "60": 1.6, "65": 2.2, "70": 3.7, "75": 4.9, "80": 6.0 },
          "reliever": { "40": 0.0, "45": 0.0, "50": 0.05, "55": 0.2, "60": 0.5, "65": 0.9, "70": 1.15, "75": 1.5, "80": 1.8 }
        },
        "yearWeights": {
          "contend": [1.00, 0.70, 0.45, 0.25, 0.12],
          "neutral": [1.00, 0.85, 0.68, 0.52, 0.38],
          "rebuild": [0.45, 0.75, 1.00, 0.95, 0.80]
        },
        "prospectPotentialWeight": 0.45,
        "youngPlayerAge": 25,
        "trade": {
          "starPremium": 0.15, "starWarFrom": 2.0, "afterControlWeight": 0.25, "growthCertainty": 0.5, "baseSalaryWeight": 0.6,
          "salaryWeightByPayroll": { "60": 0.4, "90": 0.6, "100": 0.9, "115": 1.3 }
        }
      },
      "teamMode": {
        "contendRank": 4, "rebuildRank": 8, "rebuildAverageAge": 27.0, "minGamesForRank": 30
      },
      "trade": {
        "profitMargin": { "easy": 0.08, "normal": 0.18, "hard": 0.30 },
        "rejectionsBeforeRefusal": 3,
        "refusalWeeks": 3,
        "buybackBlockSeasons": 1,
        "maxPlayersPerSide": 4,
        "cashLimit": 10.0,
        "salaryRetention": { "maxRate": 0.5, "valueRate": 1.0 },
        "gmStyles": {
          "balanced": { "prospectMultiplier": 1.0, "veteranMultiplier": 1.0, "pickMultiplier": 1.0, "marginMultiplier": 1.0, "rejectionsDelta": 0 },
          "rebuilder": { "prospectMultiplier": 1.25, "veteranMultiplier": 0.85, "pickMultiplier": 1.25, "marginMultiplier": 1.0, "rejectionsDelta": 0 },
          "winNow": { "prospectMultiplier": 0.8, "veteranMultiplier": 1.15, "pickMultiplier": 0.8, "marginMultiplier": 0.9, "rejectionsDelta": 1 },
          "hardBargainer": { "prospectMultiplier": 1.0, "veteranMultiplier": 1.0, "pickMultiplier": 1.0, "marginMultiplier": 1.6, "rejectionsDelta": -1 }
        },
        "counter": { "userFloor": 0.7, "hardUserFloor": 0.6 },
        "rumors": { "chance": 0.5, "falseRate": 0.3, "reliableIfTrue": 0.75, "reliableIfFalse": 0.3 },
        "aiTradesPerSeason": [2, 5],
        "minValueToConsider": 0.5,
        "rosterSpotCost": 2.0,
        "offers": {
          "chance": 0.2, "deadlineChance": 0.5, "deadlineWindowWeeks": 3, "needThreshold": 1.1,
          "minTargetValue": 6.0, "targetsPerTeam": 3, "protectedCore": 3, "assetPool": 10, "maxPickRound": 5,
          "maxPayerPlayers": 3, "maxPayerPicks": 2, "minAssetValue": 4.0, "fairness": 1.0, "sellTolerance": 0.9, "veteranAge": 30
        }
      },
      "softCap": {
        "cap": 120,
        "draftPickDropPlaces": 5,
        "penalties": [
          { "consecutive": 1, "rateOfExcess": 0.5, "draftPickDrop": false },
          { "consecutive": 2, "rateOfExcess": 1.0, "draftPickDrop": true },
          { "consecutive": 3, "rateOfExcess": 1.5, "draftPickDrop": true }
        ]
      },
      "roster": { "firstTeamRegistered": 28 },
      "rosterLimits": { "targetRosterSize": 58, "minRosterSize": 52 },
      "minimumSalary": { "value": 0.3 },
      "freeAgency": {
        "qualifyingSeasons": { "highSchool": 8, "college": 7, "reQualify": 4 },
        "gradeBySalaryRank": { "aTopRank": 30, "bTopRank": 60 },
        "compensation": {
          "A": { "protectedPlayers": 20, "salaryRateWithPlayer": 2.0, "salaryRateOnly": 3.0 },
          "B": { "protectedPlayers": 25, "salaryRateWithPlayer": 1.0, "salaryRateOnly": 2.0 },
          "C": { "salaryRateOnly": 1.5 }
        },
        "maxContractYears": 6
      },
      "morale": { "fa": { "moraleSwing": 1.0 } },
      "postseason": { "spots": 5 },
      "faNegotiation": {
        "patience": 4, "insultRatio": 0.8, "flexRange": [0.0, 0.0], "demandBonusShare": 0.25,
        "maxOptionRate": 0.5,
        "options": {
          "riskRate": 0.8, "chanceByRatioPercent": { "40": 0.02, "70": 0.15, "90": 0.4, "100": 0.55, "115": 0.75, "140": 0.92, "200": 0.97 },
          "chanceNoRecord": 0.1, "minPaForRate": 300, "minOutsForRate": 300, "minGamesForPace": 30,
          "plateAppearances": { "threshold": 400 }, "battingAverage": { "threshold": 0.3 }, "homeRuns": { "threshold": 20 },
          "rbi": { "threshold": 80 }, "stolenBases": { "threshold": 20 }, "innings": { "threshold": 150 }, "wins": { "threshold": 10 },
          "era": { "threshold": 3.5 }, "saves": { "threshold": 20 }, "holds": { "threshold": 20 }, "appearances": { "threshold": 55 },
          "teamPostseason": { "chanceIfMadeLast": 0.6, "chanceIfMissedLast": 0.3 }
        }
      },
      "faMarket": {
        "biddingRounds": 6,
        "askingPremium": 1.25,
        "competitionRaisePerTeam": 0.07,
        "bidIncrement": 0.06,
        "aiValueMargin": 0.85,
        "aiPayrollCeilingOfCap": 0.9,
        "salaryFloorRateOfWar": 0.8,
        "askingDropPerRound": 0.06,
        "maxYearsByAge": { "26": 6, "30": 4, "33": 2, "36": 1 },
        "weights": {
          "young": { "money": 0.45, "years": 0.15, "winning": 0.15, "loyalty": 0.15, "playingTime": 0.10 },
          "old":   { "money": 0.30, "years": 0.30, "winning": 0.15, "loyalty": 0.15, "playingTime": 0.10 }
        },
        "oldFromAge": 31,
        "signThreshold": 0.92,
        "signChancePerRound": [0.15, 0.3, 0.5, 0.7, 0.85, 1.0],
        "leftoverSalaryRate": 0.55,
        "aiBudgetShareOfFunds": 0.5,
        "modeSpending": { "contend": 1.0, "neutral": 0.85, "rebuild": 0.55 },
        "rebuildMaxAge": 27,
        "overCapPenalty": 0.75,
        "rumorExaggeration": [0.95, 1.25]
      },
      "foreignPlayers": {
        "maxPerTeam": 3,
        "maxSameType": 2,
        "activeInGame": 2,
        "newContractMaxTotal": 13.0,
        "reSignUncapped": true,
        "inSeasonReplacements": 2,
        "replacementDeadlineWeek": 19,
        "poolSizePerSeason": 40,
        "adaptation": {
          "maxRatingPenalty": 14.0,
          "firstSeasonRecovery": 0.40,
          "weeksToSettle": 12,
          "secondSeasonShare": 0.30,
          "settledFromSeason": 3
        },
        "market": {
          "originLeagues": {
            "tripleA":  { "label": "미국 트리플A", "share": 0.42, "conversion": 0.88, "salary": [3.0, 9.0] },
            "japan":    { "label": "일본",        "share": 0.16, "conversion": 0.94, "salary": [5.0, 13.0] },
            "taiwan":   { "label": "대만",        "share": 0.12, "conversion": 1.02, "salary": [2.5, 7.0] },
            "latin":    { "label": "중남미",      "share": 0.30, "conversion": 0.90, "salary": [2.5, 8.0] }
          },
          "conversionError": 0.08,
          "pitcherShare": 0.5,
          "reSignRaise": [1.05, 1.35],
          "aiSignChance": 0.75
        },
        "outflow": { "warThreshold": 3.5, "offerMultiplier": [1.6, 2.4], "chance": 0.55 }
      },
      "military": {
        "enlistDeadlineAge": 28,
        "serviceMonths": 18,
        "sangmu": {
          "chanceByOverall": { "40": 0.10, "50": 0.25, "60": 0.55, "70": 0.85, "80": 0.95 },
          "slotsPerSeason": 12
        },
        "warningAges": [26, 27],
        "earlyEnlistOverall": 48.0,
        "earlyEnlistChance": 0.35
      },
      "internationalTournament": {
        "asianGames": {
          "label": "아시안게임", "everyYears": 4, "firstSeason": 2026, "exemptionOn": "gold",
          "ageLimit": 25, "wildcards": 3, "maxPerTeam": 3, "weeksMissed": 2,
          "squadSize": 24, "startWeek": 16, "rivals": ["일본", "대만", "중국"]
        },
        "olympics": {
          "label": "올림픽", "everyYears": 4, "firstSeason": 2028, "exemptionOn": "medal",
          "ageLimit": 0, "wildcards": 0, "maxPerTeam": 3, "weeksMissed": 2,
          "squadSize": 24, "startWeek": 14, "rivals": ["미국", "일본"]
        },
        "strengthPivot": 70.0,
        "strengthSlope": 0.055,
        "goldChanceRange": [0.10, 0.70],
        "silverShareOfRest": 0.45,
        "bronzeShareOfRest": 0.40
      },
      "leagueEnvironmentEvents": {
        "noChangeChance": 0.5,
        "ballLiveliness": { "homeRunDelta": [0.05, 0.12] },
        "strikeZone": { "kBbDeltaPoints": [0.01, 0.02] },
        "announcementError": 0.35
      },
      "season": { "regularSeasonWeeks": 24 },
      "schedule": { "gamesPerTeam": 144 },
      "finance": {
        "attendance": {
          "baseByMarket": { "large": 70.0, "medium": 55.0, "small": 44.0 },
          "winPctWeight": 0.50, "fanSupportWeight": 0.40, "postseasonBonus": 0.12,
          "minShare": 0.45, "maxShare": 1.30
        },
        "broadcast": 25.0,
        "sponsorship": { "base": 16.0, "minShare": 0.30, "maxShare": 1.60, "fanSupportWeight": 0.6, "starBonus": 1.2, "starOverall": 75 },
        "postseasonRevenue": { "wildcard": 3.0, "semiPlayoff": 6.0, "playoff": 10.0, "koreanSeries": 18.0, "champion": 8.0 },
        "parentSupport": { "trustWeight": 0.35, "healthWeight": 0.35, "noParentBase": 20.0 },
        "allowedDeficitByMarket": { "large": 45.0, "medium": 35.0, "small": 28.0 }
      },
      "fanSentiment": {
        "weeklyWinWeight": 0.35, "streakBonus": 0.15, "weeklyRegression": 0.04,
        "seasonWinPctWeight": 14.0,
        "postseasonBonus": { "reached": 5, "koreanSeries": 8, "champion": 14 },
        "bottomPenalty": 6, "regressionToMean": 0.10,
        "events": {
          "bigFreeAgentSigned": 5, "franchiseStarTraded": -12, "franchiseStarReleased": -14,
          "starFreeAgentLost": -8, "rookieStarEmerged": 4, "legendRetired": 3, "managerChurn": -4
        },
        "franchiseStarOverall": 72, "bigFreeAgentSalary": 8.0, "min": 5, "max": 98
      },
      "owner": {
        "fireBelowTrust": 20, "start": 60,
        "goalReward": { "exceeded": 14, "met": 8, "missed": -12, "farMissed": -20 },
        "deficitPenaltyPerUnit": 0.25, "deficitPenaltyCap": 14, "surplusReward": 4,
        "fanChangeWeight": 0.35, "consecutiveMissPenalty": -10,
        "warningBelow": 35, "approvalRequiredBelow": 40, "approvalSalary": 6.0,
        "patience": { "easy": 1.4, "normal": 1.0, "hard": 0.75 }
      },
      "parentCompany": {
        "cycleShare": { "boom": 0.25, "normal": 0.5, "slump": 0.25 },
        "supportMultiplier": { "boom": 1.25, "normal": 1.0, "slump": 0.75 },
        "healthDelta": { "boom": 6, "normal": 0, "slump": -8 },
        "slumpCutWarningAfter": 2, "midSeasonCutShare": 0.15,
        "ownerChangeChance": 0.02, "newOwnerHealth": [45, 85]
      },
      "staffMarket": {
        "poolSize": { "coach": 10, "medical": 8, "manager": 3 },
        "salaryByGrade": { "1": 0.8, "2": 1.2, "3": 1.8, "4": 2.6, "5": 3.6 },
        "contractYears": [1, 3], "aiHireChance": 0.55,
        "competitionPremium": [1.0, 1.35], "firingCostShare": 0.6
      },
      "career": {
        "startReputation": 50,
        "expectationFromStrength": { "pivot": 68.0, "slope": 1.83 },
        "reputationGain": { "perWinAboveExpectation": 0.55, "championship": 12, "koreanSeries": 8, "postseason": 4 },
        "underperformanceShare": 0.6,
        "underdogWeight": 0.04, "underdogRange": [0.6, 1.8],
        "reputationLoss": { "fired": -10, "commentaryYear": -3 },
        "sabbaticalReputationFloor": 30,
        "offerReputation": { "strongTeam": 70, "midTeam": 55, "weakTeam": 22 },
        "offerCount": [1, 3], "commentaryIfNoOffer": true, "min": 5, "max": 99
      },
      "growth": {
        "annualGapClosure": [0.20, 0.35],
        "peakAges": { "early": [24, 28], "normal": [27, 31], "late": [29, 33] }
      },
      "aging": {
        "baseDeclinePerYear": 1.6,
        "extraDeclinePerYearOver": { "age": 34, "amount": 0.5 },
        "attributeMultiplier": {
          "contact": 1.0, "power": 0.6, "eye": 0.25, "speed": 1.7, "defense": 1.4,
          "stuff": 1.3, "control": 0.35, "groundball": 0.6, "stamina": 1.0
        }
      },
      "attributeGrowthSpeed": {
        "contact": 1.0, "power": 1.25, "eye": 1.1, "speed": 0.55, "defense": 0.8,
        "stuff": 1.0, "control": 1.2, "groundball": 0.9, "stamina": 0.9
      },
      "scouting": {
        "defaultLevel": 3,
        "focusSlotsByLevel": { "1": 3, "2": 5, "3": 8, "4": 11, "5": 15 },
        "amateurHalfWidthByLevel": { "1": 18, "2": 16, "3": 13, "4": 11, "5": 9 },
        "established": { "minPa": 200, "minOuts": 120 },
        "annualCostByLevel": { "1": 0.5, "2": 1.2, "3": 2.2, "4": 3.6, "5": 5.5 },
        "focus": {
          "narrowingPerWeek": 0.55,
          "halfWidthFloor": 3,
          "gradeSpreadWeeks": [6, 13],
          "growthTypeReliableWeeks": 16,
          "newsEveryWeeks": 3
        },
        "aiEvaluationNoise": 0.55,
        "potentialGrades": { "C": 54, "B": 64, "A": 76, "S": 84, "edgeHalfBand": 4.0 },
        "coreProspect": { "maxAge": 24, "perTeam": 3, "minGrade": "B" },
        "knownProspect": { "perClass": [3, 4], "rankNoise": 4.0, "headStartWeeks": 12 }
      }
    }
    """.trimIndent(),
)

internal const val TEST_SEASON = 2026

internal fun testBatter(
    id: String,
    rating: Int = 50,
    potential: Int = 70,
    position: Position = Position.SHORTSTOP,
    age: Int = 19,
    teamId: TeamId? = null,
    origin: Origin = Origin.HIGH_SCHOOL,
    noiseSeed: Int = id.hashCode(),
): Batter = Batter(
    id = PlayerId(id),
    name = "타자$id",
    birthYear = TEST_SEASON - age,
    throwsWith = Hand.RIGHT,
    bats = Hand.RIGHT,
    origin = origin,
    debutSeason = TEST_SEASON,
    teamId = teamId,
    rosterLevel = RosterLevel.FUTURES,
    contract = Contract(0.3, 1, 0.0, 8, 0, ContractType.ROOKIE),
    military = MilitaryStatus.Unfulfilled(28),
    condition = Condition.HEALTHY,
    hidden = HiddenTraits(
        potential = Attribute.batterAttributes.associateWith { potential },
        growthType = GrowthType.NORMAL,
        durability = 60,
        volatility = 50,
        platoonSplit = 35,
        scoutingNoiseSeed = noiseSeed,
    ),
    primaryPosition = position,
    defenseFitness = mapOf(position to rating),
    ratings = BatterRatings(rating, rating, rating, rating, rating),
)

internal fun testPitcher(
    id: String,
    rating: Int = 50,
    potential: Int = 70,
    role: PitcherRole = PitcherRole.STARTER,
    age: Int = 19,
    teamId: TeamId? = null,
    noiseSeed: Int = id.hashCode(),
    origin: Origin = Origin.HIGH_SCHOOL,
    adaptability: Int? = null,
    debutSeason: Int = TEST_SEASON,
): Pitcher = Pitcher(
    id = PlayerId(id),
    name = "투수$id",
    birthYear = TEST_SEASON - age,
    throwsWith = Hand.RIGHT,
    bats = Hand.RIGHT,
    origin = origin,
    debutSeason = debutSeason,
    teamId = teamId,
    rosterLevel = RosterLevel.FUTURES,
    contract = Contract(0.3, 1, 0.0, 8, 0, ContractType.ROOKIE),
    military = MilitaryStatus.Unfulfilled(28),
    condition = Condition.HEALTHY,
    hidden = HiddenTraits(
        potential = Attribute.pitcherAttributes.associateWith { potential },
        growthType = GrowthType.NORMAL,
        durability = 60,
        volatility = 50,
        platoonSplit = 35,
        adaptability = adaptability,
        scoutingNoiseSeed = noiseSeed,
    ),
    role = role,
    ratings = PitcherRatings(rating, rating, rating, rating),
    topSpeedKmh = 145,
)

internal fun prospect(
    id: String,
    rating: Int = 50,
    potential: Int = 70,
    pitcher: Boolean = false,
    highSchool: Boolean = true,
): DraftProspect = DraftProspect(
    player = if (pitcher) {
        testPitcher(id, rating, potential, age = if (highSchool) 19 else 22)
    } else {
        testBatter(
            id = id,
            rating = rating,
            potential = potential,
            age = if (highSchool) 19 else 22,
            origin = if (highSchool) Origin.HIGH_SCHOOL else Origin.COLLEGE,
        )
    },
    school = if (highSchool) "청림고" else "청림대",
    heightCm = 183,
    weightKg = 85,
)

// ---------- M7 시장 테스트용 리그 ----------

internal fun testTeam(id: String, funds: Double = 40.0, pick: Int = 1) = baseballgm.model.Team(
    id = TeamId(id),
    name = "구단 $id",
    city = "도시",
    nickname = id,
    parentCompany = baseballgm.model.ParentCompany("모기업 $id", annualSupport = 20.0, financialHealth = 60),
    tier = "mid",
    keyword = "테스트",
    marketSize = "medium",
    parkFactor = 1.0,
    fanVolatility = 1.0,
    ownerGoal = "포스트시즌 진출",
    operatingFunds = funds,
    draftPick = pick,
    fanSupport = 55,
    ownerTrust = 60,
    managerId = null,
    coachIds = emptyList(),
    medicalStaffIds = emptyList(),
    generalManagerId = null,
)

/**
 * 시장 테스트용 작은 리그.
 *
 * 두 구단이 서로 남는 포지션과 빈 포지션을 가지고 있어서, 트레이드가 성립할 조건을 만들어 둔다.
 */
internal fun testLeague(
    teams: List<baseballgm.model.Team> = listOf(testTeam("AAA", pick = 1), testTeam("BBB", pick = 2)),
    players: List<baseballgm.model.Player>,
    freeAgents: List<PlayerId> = emptyList(),
    faOrigins: Map<PlayerId, TeamId> = emptyMap(),
): baseballgm.league.League = baseballgm.league.League(
    season = TEST_SEASON,
    seed = 1L,
    salaryCap = 120.0,
    teams = teams,
    players = players,
    managers = emptyList(),
    coaches = emptyList(),
    medicalStaff = emptyList(),
    generalManagers = emptyList(),
    schedule = baseballgm.league.Schedule(TEST_SEASON, emptyList()),
    freeAgentPool = freeAgents,
    draftRights = DraftRights.initial(TEST_SEASON, teams.map { it.id }, rounds = 3, years = 2),
    faOrigins = faOrigins,
)

/**
 * 한 팀의 기본 선수단. 포지션을 골고루 채워 포지션 필요도가 극단으로 가지 않게 한다.
 *
 * **1군으로 만든다** — 팀 전력([StrengthCalculator.of])은 1군 선수만 세기 때문에, 2군으로 두면
 * 전력이 0 이 되어 기대 승수·평판 계산이 전부 무의미해진다.
 */
internal fun rosterFor(
    teamId: String,
    rating: Int = 60,
    prefix: String = teamId,
    salary: Double = 3.0,
): List<baseballgm.model.Player> {
    val positions = Position.fielding
    fun contract() = Contract(salary, 3, 0.0, 5, 5, ContractType.STANDARD)
    val batters = positions.mapIndexed { index, position ->
        testBatter("$prefix-B$index", rating = rating, position = position, age = 27, teamId = TeamId(teamId))
            .copy(rosterLevel = RosterLevel.FIRST_TEAM, contract = contract())
    }
    val starters = (1..5).map {
        testPitcher("$prefix-SP$it", rating = rating, age = 27, teamId = TeamId(teamId), role = PitcherRole.STARTER)
            .copy(rosterLevel = RosterLevel.FIRST_TEAM, contract = contract())
    }
    val relievers = (1..7).map {
        testPitcher("$prefix-RP$it", rating = rating, age = 27, teamId = TeamId(teamId), role = PitcherRole.RELIEVER)
            .copy(rosterLevel = RosterLevel.FIRST_TEAM, contract = contract())
    }
    return batters + starters + relievers
}
