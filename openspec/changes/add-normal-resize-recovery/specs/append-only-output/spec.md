## ADDED Requirements

### Requirement: Append output follows committed resize recovery
When a successful normal-screen resize recovery has placed detached durable rows immediately above the retained live frame, later append-only output SHALL operate only on the live-frame region and SHALL preserve chronological durable-output order.

#### Scenario: Append follows recovered history
- **WHEN** durable `A` is recovered during resize and durable `B` is later published through `appendToScrollback`
- **THEN** visible physical order SHALL be recovered `A`, appended `B`, then the retained live frame

#### Scenario: Append does not clear recovered rows
- **WHEN** append publication moves from the retained cursor to live-frame row zero
- **THEN** it SHALL clear and relocate only the replaceable live-frame region and SHALL NOT repaint, erase, or include recovery rows in `previousFrame`

#### Scenario: Multiple appends follow recovery
- **WHEN** multiple append operations are accepted after one recovery commit
- **THEN** they SHALL retain existing FIFO completion/publication order between the recovered prefix and retained live frame

#### Scenario: Empty recovery changes no append behavior
- **WHEN** an eligible resize commits zero recovery rows
- **THEN** later append admission, output planning, frame relocation, callbacks, and typed-control ownership SHALL remain unchanged

#### Scenario: Recovery adds no image identity ownership
- **WHEN** text-only recovery precedes a retained frame
- **THEN** append Kitty ID remapping, the bounded append ownership ledger, retained-iTerm2 rejection, and retained cleanup semantics SHALL remain unchanged
