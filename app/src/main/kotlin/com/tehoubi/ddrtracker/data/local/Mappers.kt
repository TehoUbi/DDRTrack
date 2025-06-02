package com.tehoubi.ddrtracker.data.local

import com.tehoubi.ddrtracker.core.model.ClearLamp
import com.tehoubi.ddrtracker.core.model.Difficulty
import com.tehoubi.ddrtracker.core.model.Grade
import com.tehoubi.ddrtracker.core.model.Judgments
import com.tehoubi.ddrtracker.core.model.PlayStyle
import com.tehoubi.ddrtracker.domain.model.Play
import com.tehoubi.ddrtracker.domain.model.PlaySource
import java.time.Instant

fun PlayEntity.toDomain() = Play(
    id = id,
    songTitle = songTitle,
    artist = artist,
    style = PlayStyle.valueOf(style),
    difficulty = Difficulty.valueOf(difficulty),
    level = level,
    score = score,
    exScore = exScore,
    grade = Grade.valueOf(grade),
    lamp = ClearLamp.valueOf(lamp),
    judgments = Judgments(marvelous, perfect, great, good, ok, miss),
    maxCombo = maxCombo,
    fast = fast,
    slow = slow,
    failed = failed,
    playedAt = Instant.ofEpochMilli(playedAt),
    source = PlaySource.valueOf(source),
    scanConfidence = scanConfidence,
    note = note,
)

fun Play.toEntity(updatedAt: Long) = PlayEntity(
    id = id,
    songKey = songKey,
    songTitle = songTitle,
    artist = artist,
    style = style.name,
    difficulty = difficulty.name,
    level = level,
    score = score,
    exScore = exScore,
    grade = grade.name,
    lamp = lamp.name,
    marvelous = judgments.marvelous,
    perfect = judgments.perfect,
    great = judgments.great,
    good = judgments.good,
    ok = judgments.ok,
    miss = judgments.miss,
    maxCombo = maxCombo,
    fast = fast,
    slow = slow,
    failed = failed,
    playedAt = playedAt.toEpochMilli(),
    source = source.name,
    scanConfidence = scanConfidence,
    note = note,
    updatedAt = updatedAt,
)
