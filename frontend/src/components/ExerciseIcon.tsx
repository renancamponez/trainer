// Original simple line-drawing diagrams for each exercise. Stick figures in currentColor
// so they inherit the theme; the accent colour marks the key cue for that movement.
const A = "#fc4c02"; // accent for the coaching cue

const svg = (children: React.ReactNode) => (
  <svg viewBox="0 0 120 90" width="100%" height="100%"
       fill="none" stroke="currentColor" strokeWidth={2.4}
       strokeLinecap="round" strokeLinejoin="round">
    {children}
  </svg>
);

const ICONS: Record<string, React.ReactNode> = {
  "face-pull": svg(<>
    <line x1="14" y1="12" x2="60" y2="34" stroke={A} />
    <line x1="14" y1="12" x2="14" y2="80" />
    <circle cx="74" cy="34" r="7" />
    <line x1="74" y1="41" x2="74" y2="62" />
    <line x1="74" y1="62" x2="66" y2="82" /><line x1="74" y1="62" x2="82" y2="82" />
    <path d="M74 44 L58 30 L70 30" stroke={A} />
    <path d="M74 44 L90 30 L78 30" stroke={A} />
  </>),
  "chest-row": svg(<>
    <line x1="20" y1="82" x2="70" y2="48" strokeWidth={3} opacity={0.5} />
    <circle cx="74" cy="44" r="7" />
    <line x1="70" y1="49" x2="40" y2="70" />
    <path d="M62 55 L50 46" stroke={A} /><path d="M50 46 L44 58" stroke={A} />
    <line x1="24" y1="82" x2="96" y2="82" />
  </>),
  "lat-pulldown": svg(<>
    <line x1="60" y1="8" x2="60" y2="30" stroke={A} />
    <circle cx="56" cy="36" r="7" />
    <path d="M56 30 L62 12" stroke={A} />
    <line x1="56" y1="43" x2="52" y2="62" />
    <line x1="52" y1="62" x2="70" y2="62" /><line x1="70" y1="62" x2="74" y2="82" />
    <line x1="52" y1="62" x2="44" y2="82" /><line x1="44" y1="82" x2="30" y2="82" />
    <line x1="20" y1="82" x2="96" y2="82" />
  </>),
  "ytw": svg(<>
    <circle cx="30" cy="30" r="6" /><line x1="30" y1="36" x2="30" y2="62" />
    <path d="M30 40 L18 22" stroke={A} /><path d="M30 40 L42 22" stroke={A} />
    <circle cx="90" cy="30" r="6" /><line x1="90" y1="36" x2="90" y2="62" />
    <path d="M90 42 L74 40" stroke={A} /><path d="M90 42 L106 40" stroke={A} />
    <text x="24" y="80" fontSize="12" fill={A} stroke="none">Y</text>
    <text x="58" y="80" fontSize="12" fill={A} stroke="none">T</text>
    <text x="86" y="80" fontSize="12" fill={A} stroke="none">W</text>
  </>),
  "split-squat": svg(<>
    <circle cx="52" cy="20" r="7" />
    <line x1="52" y1="27" x2="52" y2="52" />
    <path d="M52 52 L52 68 L44 82" /> {/* front leg vertical shin */}
    <path d="M52 52 L74 66 L92 60" stroke={A} /> {/* rear leg to bench */}
    <line x1="84" y1="60" x2="100" y2="60" strokeWidth={3} opacity={0.6} />
    <line x1="20" y1="82" x2="100" y2="82" />
  </>),
  "rdl": svg(<>
    <circle cx="34" cy="30" r="7" />
    <path d="M40 32 L72 40" stroke={A} /> {/* flat back hinging */}
    <path d="M72 40 L74 82" /> {/* legs */}
    <line x1="66" y1="52" x2="82" y2="52" strokeWidth={3} stroke={A} /> {/* bar near shins */}
    <path d="M40 32 L30 30" stroke={A} /><path d="M72 40 L74 60" opacity={0} />
    <line x1="20" y1="82" x2="100" y2="82" />
  </>),
  "dead-bug": svg(<>
    <line x1="24" y1="66" x2="86" y2="66" strokeWidth={3} opacity={0.5} /> {/* floor / flat back */}
    <circle cx="30" cy="60" r="6" />
    <path d="M40 64 L64 64" /> {/* torso on floor */}
    <path d="M48 64 L48 44" stroke={A} /><path d="M48 44 L40 34" stroke={A} /> {/* arm up */}
    <path d="M64 64 L86 52" stroke={A} /><path d="M86 52 L96 40" stroke={A} /> {/* opp leg up */}
    <path d="M48 64 L40 82" opacity={0.5} />
  </>),
  "suitcase-carry": svg(<>
    <circle cx="60" cy="18" r="7" />
    <line x1="60" y1="25" x2="60" y2="58" />
    <line x1="60" y1="58" x2="54" y2="82" /><line x1="60" y1="58" x2="66" y2="82" />
    <line x1="60" y1="32" x2="78" y2="34" /><line x1="78" y1="34" x2="80" y2="58" stroke={A} />
    <rect x="74" y="58" width="12" height="10" rx="2" fill={A} stroke={A} />
    <line x1="42" y1="18" x2="42" y2="70" strokeDasharray="3 3" opacity={0.5} />
  </>),
  "t-spine": svg(<>
    <circle cx="82" cy="52" r="12" opacity={0.5} /> {/* roller */}
    <circle cx="40" cy="34" r="6" /> {/* head draped back */}
    <path d="M46 38 L82 46" stroke={A} /> {/* upper back over roller */}
    <path d="M82 46 L96 70 L108 70" /> {/* hips/legs down */}
    <path d="M96 70 L96 82" />
    <line x1="20" y1="82" x2="110" y2="82" />
  </>),
  "couch": svg(<>
    <line x1="88" y1="6" x2="88" y2="82" strokeWidth={3} opacity={0.5} /> {/* wall */}
    <circle cx="50" cy="24" r="7" /><line x1="50" y1="31" x2="50" y2="56" />
    <path d="M50 56 L38 78" /> {/* front leg */}
    <path d="M50 56 L74 66 L84 40" stroke={A} /> {/* rear shin up wall */}
    <line x1="20" y1="82" x2="88" y2="82" />
  </>),
  "doorway-pec": svg(<>
    <line x1="30" y1="6" x2="30" y2="82" strokeWidth={3} opacity={0.5} /> {/* frame */}
    <circle cx="54" cy="26" r="7" /><line x1="54" y1="33" x2="54" y2="60" />
    <path d="M54 38 L36 34 L34 20" stroke={A} /> {/* forearm on frame, elbow 90 */}
    <line x1="54" y1="60" x2="46" y2="82" /><line x1="54" y1="60" x2="70" y2="78" />
    <path d="M74 60 L86 58" stroke={A} /><path d="M82 54 L88 58 L82 62" stroke={A} /> {/* rotate away */}
  </>),
  "band-pull-apart": svg(<>
    <circle cx="60" cy="20" r="7" /><line x1="60" y1="27" x2="60" y2="56" />
    <line x1="60" y1="56" x2="54" y2="82" /><line x1="60" y1="56" x2="66" y2="82" />
    <path d="M60 36 L30 40" /><path d="M60 36 L90 40" />
    <path d="M22 40 L30 40" stroke={A} /><path d="M4 40 L14 36 L14 44 Z" fill={A} stroke={A} />
    <path d="M90 40 L98 40" stroke={A} /><path d="M116 40 L106 36 L106 44 Z" fill={A} stroke={A} />
  </>),
  "chin-tuck": svg(<>
    <circle cx="66" cy="36" r="13" /> {/* head */}
    <path d="M66 49 L66 78" /> {/* neck/body */}
    <path d="M60 36 L44 36" stroke={A} /><path d="M50 31 L44 36 L50 41" stroke={A} /> {/* slide back arrow */}
    <circle cx="88" cy="34" r="13" strokeDasharray="3 3" opacity={0.4} /> {/* forward-head ghost */}
  </>),
  "workstation": svg(<>
    <rect x="10" y="18" width="44" height="30" rx="2" /> {/* monitor */}
    <line x1="32" y1="48" x2="32" y2="58" /><line x1="22" y1="58" x2="42" y2="58" />
    <line x1="10" y1="22" x2="86" y2="22" strokeDasharray="4 3" stroke={A} /> {/* eye-level line */}
    <circle cx="86" cy="26" r="6" /><path d="M86 32 L86 50" />
    <path d="M86 50 L100 50" /> {/* thigh 90 */}
    <path d="M100 50 L100 72" /> {/* shin 90 */}
    <path d="M86 38 L98 40" /> {/* arm */}
    <line x1="72" y1="50" x2="112" y2="50" strokeWidth={3} opacity={0.5} /> {/* desk */}
    <line x1="94" y1="72" x2="110" y2="72" /> {/* floor / feet flat */}
  </>),
};

export default function ExerciseIcon({ name }: { name: string }) {
  return (
    <div style={{ width: "100%", height: "100%", color: "#9fb2c4" }}>
      {ICONS[name] ?? svg(<circle cx="60" cy="45" r="20" opacity={0.3} />)}
    </div>
  );
}
