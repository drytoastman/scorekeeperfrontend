BASE = [IP/URL]/[json|xml]/[seriesname]

BASE - returns a list of events, challenges, classes and indexes
{
    events: [ Event ],
    challenges: [ Challenge ],
    classes: [ Class ],
    indexes: [ Index ],
    settings: { name/value pairs of series settings }
}


BASE/event/[eventid] - Returns the entire results data structs for this event including calculated decorations and internally ordered by finishing position
{
    [classcode]: [ EntrantResult ]
}

BASE/challenge/[challengeid] - Returns the entire results data structure for the challenge including calculated decorations.  It is an array of Round objects.
[
    {  e1: TopEntrant, e2: BottomEntrant }
]

BASE/champ - Returns the entire results data structs for the championship calculations for each class and internally order by finishing position
{
    [classcode]: [ ChampEntry ]
}


