"""Untrusted model output is a bounded patch, never executable code."""
from typing import Literal
from pydantic import BaseModel, ConfigDict, Field

class StrictModel(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True)

class Palette(StrictModel):
    background: str = Field(pattern=r'^#[0-9A-Fa-f]{6}$')
    surface: str = Field(pattern=r'^#[0-9A-Fa-f]{6}$')
    foreground: str = Field(pattern=r'^#[0-9A-Fa-f]{6}$')
    muted: str = Field(pattern=r'^#[0-9A-Fa-f]{6}$')
    accent: str = Field(pattern=r'^#[0-9A-Fa-f]{6}$')

class ThemePatch(StrictModel):
    light: Palette | None = None
    dark: Palette | None = None
    columns: int | None = Field(default=None, ge=3, le=6)
    drawer: Literal['GRID', 'LIST', 'CATEGORIES', 'RADIAL', 'FREQUENT', 'ALPHABETICAL', 'SEARCH_FIRST'] | None = None
    cornerRadius: int | None = Field(default=None, ge=0, le=40)
    iconRadius: int | None = Field(default=None, ge=0, le=28)
    monochromeIcons: bool | None = None
    typography: Literal['sans', 'mono', 'serif'] | None = None
    spacing: int | None = Field(default=None, ge=8, le=24)
    dockOpacity: float | None = Field(default=None, ge=.2, le=1)
    gradient: bool | None = None
    reducedMotion: bool | None = None
    clockStyle: Literal['DIGITAL', 'DOT', 'STACKED'] | None = None
    homeStyle: Literal['CALM', 'GEOMETRIC', 'SHELF', 'ORCHARD', 'ONE'] | None = None
    wallpaperStyle: Literal['SOLID', 'AURORA', 'ORBS', 'GRAIN'] | None = None

class Theme(ThemePatch):
    schemaVersion: Literal[1, 2] = 2
    id: str = Field(pattern=r'^[a-z][a-z0-9_-]{0,39}$')
    name: str = Field(min_length=1, max_length=60)
    description: str = Field(max_length=200)
    light: Palette
    dark: Palette

class DesignRequest(StrictModel):
    prompt: str = Field(min_length=1, max_length=2000)
    current: Theme
    route: Literal['fast', 'complex'] = 'fast'
    revision: int = Field(ge=0)

class ImageDesignRequest(DesignRequest):
    imageBase64: str = Field(min_length=1, max_length=1400000)

class Proposal(StrictModel):
    explanation: str = Field(min_length=1, max_length=500)
    patch: ThemePatch

class WidgetDesign(StrictModel):
    title: str = Field(min_length=1, max_length=40)
    kind: Literal['CLOCK', 'BATTERY', 'TIMER', 'COUNTER', 'BIRTHDAYS', 'CALENDAR']
    shape: Literal['ROUNDED', 'CIRCLE', 'HEXAGON', 'STAR', 'RECTANGLE'] = 'ROUNDED'
    durationSeconds: int = Field(default=1500, ge=1, le=86400)

class WidgetRequest(StrictModel):
    prompt: str = Field(min_length=1, max_length=2000)
    route: Literal['fast', 'complex'] = 'fast'
    revision: int = Field(ge=0)

class WidgetProposal(StrictModel):
    explanation: str = Field(min_length=1, max_length=500)
    widget: WidgetDesign
