//! Human-readable names for RCCE2 wire message types, mirroring `Packets.bb`
//! (`P_*`) plus the ENet-level type 0 (new client) and the locally-synthesized
//! disconnect sentinels (200-202, `RCEnet.bb`). Used only for the Phase-0
//! protocol trace so a live client connection reads as a sequence of named
//! packets instead of bare type numbers — the decode/dispatch that *acts* on
//! these lands in Phase 1+.

/// Symbolic name for a wire message type byte, or `"P_?(<n>)"` if unknown.
pub fn name(t: u8) -> &'static str {
    match t {
        0 => "NewClient",
        1 => "P_CreateAccount",
        2 => "P_VerifyAccount",
        3 => "P_FetchCharacter",
        4 => "P_CreateCharacter",
        5 => "P_DeleteCharacter",
        6 => "P_ChangePassword",
        7 => "P_FetchActors",
        8 => "P_FetchItems",
        9 => "P_ChangeArea",
        10 => "P_FetchUpdateFiles",
        11 => "P_NewActor",
        12 => "P_StartGame",
        13 => "P_ActorGone",
        14 => "P_StandardUpdate",
        15 => "P_InventoryUpdate",
        16 => "P_ChatMessage",
        17 => "P_WeatherChange",
        18 => "P_AttackActor",
        19 => "P_ActorDead",
        20 => "P_RightClick",
        21 => "P_Dialog",
        22 => "P_StatUpdate",
        23 => "P_QuestLog",
        24 => "P_GoldChange",
        25 => "P_NameChange",
        26 => "P_KnownSpellUpdate",
        27 => "P_SpellUpdate",
        28 => "P_CreateEmitter",
        29 => "P_Sound",
        30 => "P_AnimateActor",
        31 => "P_ActionBarUpdate",
        32 => "P_XPUpdate",
        33 => "P_ScreenFlash",
        34 => "P_Music",
        35 => "P_OpenTrading",
        36 => "P_ActorEffect",
        37 => "P_Projectile",
        38 => "P_PartyUpdate",
        39 => "P_AppearanceUpdate",
        40 => "P_CloseTrading",
        41 => "P_UpdateTrading",
        42 => "P_SelectScenery",
        43 => "P_ItemScript",
        44 => "P_EatItem",
        45 => "P_ItemHealth",
        46 => "P_Jump",
        47 => "P_Dismount",
        48 => "P_FloatingNumber",
        49 => "P_RepositionActor",
        50 => "P_Speech",
        51 => "P_ProgressBar",
        52 => "P_BubbleMessage",
        53 => "P_ScriptInput",
        60 => "P_KickedPlayer",
        61 => "P_Examine",
        62 => "P_Trade",
        // Locally-synthesized disconnect sentinels — must never arrive from a
        // remote peer (RCEnet.bb rejects them; spoofing one is a kick exploit).
        200 => "RCE_PlayerTimedOut",
        201 => "RCE_PlayerHasLeft",
        202 => "RCE_PlayerKicked",
        _ => "P_?",
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn known_types_named() {
        assert_eq!(name(0), "NewClient");
        assert_eq!(name(2), "P_VerifyAccount");
        assert_eq!(name(62), "P_Trade");
        assert_eq!(name(202), "RCE_PlayerKicked");
    }

    #[test]
    fn unknown_type_falls_back() {
        assert_eq!(name(254), "P_?");
    }

    /// The names must agree with the shared `rcce-net` constants for the subset
    /// it defines — a divergence here would mean the trace lies about what the
    /// client actually sent.
    #[test]
    fn agrees_with_rcce_net_subset() {
        use rcce_net::packet_id as p;
        assert_eq!(name(p::CREATE_ACCOUNT), "P_CreateAccount");
        assert_eq!(name(p::VERIFY_ACCOUNT), "P_VerifyAccount");
        assert_eq!(name(p::STANDARD_UPDATE), "P_StandardUpdate");
        assert_eq!(name(p::TRADE), "P_Trade");
    }
}
