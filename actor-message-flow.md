# Actor 메시지 흐름

## WebSocket Session 관련 Actor 초기화

> 사용자가 최초로 WebSocket Session으로 연결되었을 때 필요한 Actor를 초기화하는 과정

### 1. ClientSessionActor Spawned

> 클라이언트 WebSocket Session에 대응하는 ClientSessionActor Spawned 과정

- ClientSessionActorManagementService -> GuardianActor
  - GuardianActor.SpawnClientSession 메시지 전달
    - Client WebSocket Session 연결 시 외부로부터 ClientSessionActor Spawn을 요청하기 위한 메시지
- GuardianActor -> ClientSessionActorManagementService.AskPattern
  - GuardianActor.SpawnedClientSession 메시지 전달
    - ClientSessionActor spawn 완료 후 ActorRef를 ClientSessionActorManagementService.AskPattern에 전달하기 위한 메시지
    - AskPattern을 사용하므로 해당 과정의 메시지를 모두 GuardianActor에 위치시킴

### 2. 사용자가 참여하고 있는 채널 ID 조회

> Spawned 된 ClientSessionActor가 참여하고 있는 채널에 대한 ID 조회 과정

- ClientSessionActor -> ChannelMembershipPort
  - ChannelMembershipPort.findParticipatingChannels() 메서드를 호출해 참여하고 있는 채널에 대한 ID 조회
    - 메시지 전송 과정은 아니지만 수행 결과로 찾은 채널 ID를 ClientSessionActor에게 전달 
- ChannelMembershipPort -> ClientSessionActor
  - ClientSessionProtocol.FoundRegisteredChannelIds 메시지 전달
    - 사용자가 참여하고 있는 모든 채널의 ID 목록을 전달받기 위한 메시지

### 3. 사용자가 참여하고 있는 채널에 대한 ChannelReaderActor 요청

> ClientSessionActor가 메시지를 동기화받기 위해 Primary-Secondary 구조 중 Secondary에 해당하는 ChannelReaderActor에 대한 ActorRef를 초기화하는 과정

- ClientSessionActor -> ChannelReaderRegistryActor
  - ChannelReaderRegistryActor.GetChannelReaderActor 메시지 전달
    - ChannelReaderActor의 ActorRef 조회를 요청하는 메시지
- ChannelReaderRegistryActor -> ClientSessionActor
  - ClientSessionActor.FoundChannelReaders 메시지 전달
    - 요청한 채널 ID에 대해 ChannelReaderRegistryActor가 Singleton 방식으로 관리하는 ChannelReaderActor ActorRef를 전달받는 메시지

### 4. ChannelReaderActor에 ClientSessionActor 자기 자신 등록 요청

> ChannelReaderActor가 해당 ClientSessionActor에게 채팅 이벤트를 푸시하고 Actor 종료를 감지할 수 있도록 구독자로 등록하는 과정

- ClientSessionActor -> ChannelReaderActor
  - ChannelReaderProtocol.RegisterClientSession 메시지 전달
    - ChannelReaderActor가 ClientSessionActor를 구독자로 등록해 채팅 이벤트를 fan-out하고 종료 신호를 감시하기 위한 메시지

### 5. 채팅 히스토리 동기화 과정

> 사용자가 채널에 참여했을 때 채팅 히스토리를 확인할 수 있도록 동기화하는 과정

- ClientSessionActor -> ChannelReaderActor
  - ChannelReaderProtocol.RequestInitialHistory 메시지 전달
    - ChannelEntity-ChannelReaderActor까지 동기화된 채팅 히스토리를 요청받는 메시지
- ChannelReaderActor -> ClientSessionActor
  - ClientSessionProtocol.FoundHistory 메시지 전달
    - 사용자가 채널에 최초 입장했을 때 동기화된 채팅 히스토리를 전달받는 메시지

### 6. ChannelReaderActor ActorRef 확보 전 히스토리 요청 처리

> 특정 채널의 ChannelReaderActor를 아직 조회하지 못했을 때 ClientSessionActor가 RequestHistory 요청을 보관해 두었다가 ActorRef 확보 직후 처리하는 과정

- ClientSessionActor -> ChannelReaderRegistryActor
  - ChannelReaderRegistryActor.GetChannelReaderActor 메시지 전달
    - ChannelReaderActor의 ActorRef 조회를 요청하는 메시지
- ChannelReaderRegistryActor -> ClientSessionActor
  - ClientSessionActor.FoundChannelReaders 메시지 전달
    - 요청한 채널 ID에 대해 ChannelReaderRegistryActor가 Singleton 방식으로 관리하는 ChannelReaderActor ActorRef를 전달받는 메시지
- ClientSessionActor -> ChannelReaderActor
  - ChannelReaderProtocol.GetHistory 메시지 전달
    - pendingRequestHistory에 보관했던 범위 요청을 ChannelReaderActor에게 전달하는 메시지
- ChannelReaderActor 또는 ChannelEntity -> ClientSessionActor
  - ClientSessionProtocol.DeliverHistory 메시지 전달
    - ChannelReaderActor가 직접 응답하거나 ChannelEntity가 ResolveHistory로 찾아온 히스토리를 전달받는 메시지
- ClientSessionActor -> MessageStoragePort
  - MessageStoragePort.findHistory() 메서드 호출
    - ChannelReaderActor와 ChannelEntity가 전달한 히스토리가 비어 있는 경우 영속 저장소에서 직접 조회하기 위한 호출
- MessageStoragePort -> ClientSessionActor
  - ClientSessionProtocol.FoundHistory 메시지 전달
    - 영속 저장소에서 조회한 채팅 히스토리를 ClientSessionActor에게 전달하는 메시지

## 채팅 히스토리 조회 요청

### 1. 외부에서 채팅 히스토리 조회 요청

> 사용자가 채널 UI에서 스크롤 등을 통해 채팅 히스토리 조회 요청

- 애플리케이션 서비스 -> ClientSessionActor
  - ClientSessionProtocol.RequestHistory 메시지 전달
    - 채팅 히스토리를 요청하는 메시지

### 2. ClientSessionActor가 해당 채널의 ChannelReaderActor에게 채팅 히스토리 조회 요청

> ClientSessionActor가 참여하고 있는 채널에 대해 관리하고 있는 ChannelReaderActor에게 채팅 히스토리를 요청하는 과정

- ClientSessionActor -> ChannelReaderActor
  - ChannelReaderProtocol.GetHistory 메시지 전달
    - 채팅 히스토리에 대한 범위 요청을 받는 메시지

### 3. 동기화된 채팅 히스토리를 ClientSessionActor에게 전달

> ChannelReaderActor에서 동기화된 채팅 히스토리를 전달해 ClientSessionActor에게 전달하는 과정
> 만약 ChannelReaderActor가 채팅 히스토리를 동기화받지 못해 빈 메시지를 받았다면 ChannelEntity에게 요청

- ChannelReaderActor에 동기화된 채팅 히스토리가 있는 경우
  - ChannelReaderActor -> ClientSessionActor
    - ClientSessionProtocol.DeliverHistory 메시지 전달
      - 요청한 채팅 히스토리를 전달받는 메시지
- ChannelReaderActor에 동기화된 채팅 히스토리가 없는 경우
  - ChannelReaderActor -> ChannelEntity
    - ChannelProtocol.ResolveHistory 메시지 전달
      - ChannelReaderActor가 아직 동기화받지 못한 채팅 히스토리를 ChannelEntity가 요청받는 메시지
  - ChannelEntity -> ClientSessionActor
    - ClientSessionProtocol.DeliverHistory 메시지 전달
      - 요청한 채팅 히스토리를 전달받는 메시지
- ChannelReaderActor와 ChannelEntity 모두 히스토리를 전달하지 못한 경우
  - ClientSessionActor -> MessageStoragePort
    - MessageStoragePort.findHistory() 메서드 호출
      - 영속 저장소에서 직접 채팅 히스토리를 조회하기 위한 호출
  - MessageStoragePort -> ClientSessionActor
    - ClientSessionProtocol.FoundHistory 메시지 전달
      - 영속 저장소에서 조회된 채팅 히스토리를 ClientSessionActor에게 전달하는 메시지

## 새로운 채팅 메시지 입력

### 1. WebSocketSession으로 새로운 채팅 메시지 전달

> 사용자가 해당 채널 UI에 새로운 채팅 입력

- WebSocketSession -> ChannelEntity
  - ChannelProtocol.SendMessage 메시지 전달
    - 새로운 채팅 메시지를 전달받는 메시지

### 2. 새로운 채팅 메시지 영속화

> 입력받은 채팅 메시지를 영속화하고 그 결과를 전달받는 과정

- ChannelEntity -> MessageStoragePort
  - MessageStoragePort.store()로 전달받은 메시지에 대한 영속화 진행
    - 메시지 전송 과정은 아니지만 수행 결과로 ChannelEntity에게 영속화가 완료된 메시지를 전달하게 됨
- MessageStoragePort -> ChannelEntity
  - ChannelProtocol.SyncPersistedMessage 메시지 전달
    - 영속화된 채팅 메시지를 ChannelEntity에게 전달하는 메시지

### 3. 영속화한 새로운 채팅 메시지를 동기화

> ChannelEntity에서 영속화한 메시지를 Secondary인 ChannelReaderActor에게 전파하는 과정

- ChannelEntity -> ChannelReaderActor
  - ChannelReaderProtocol.SyncNewMessage 메시지 전달
    - ChannelEntity에 동기화된 채팅 히스토리를 전달받는 메시지

### 4. 동기화한 새로운 채팅 메시지를 사용자 UI에 전달

> ChannelReaderActor에서 동기화한 새로운 채팅 메시지를 사용자 UI까지 전달하는 과정

- ChannelReaderActor -> ClientSessionActor
  - ClientSessionProtocol.DeliverNewMessage 메시지 전달
    - ChannelEntity-ChannelReaderActor까지 완전히 동기화된, 새로운 채팅 메시지를 동기화받는 메시지
- ClientSessionActor -> ClientMessageSender
  - ClientMessageSender.sendMessage() 메서드를 호출해 WebSocketSession에게 채팅 메시지 전달

## 채팅 메시지 수정

### 1. 채팅 메시지 수정 요청

> 사용자가 채팅 메시지 수정 요청

- 애플리케이션 서비스 -> ChannelEntity
  - ChannelProtocol.UpdateMessage 메시지 전달
    - 기존 채팅 메시지 수정을 요청받는 메시지

### 2. 채팅 메시지 수정 영속화

> 채팅 메시지 수정 내용을 영속화하고, 그 결과를 ChannelEntity에게 전달하는 과정

- ChannelEntity -> MessageStoragePort
  - MessageStoragePort.update()로 전달받은 메시지에 대한 영속화 진행
    - 메시지 전송 과정은 아니지만 수행 결과로 메시지를 전달하게 됨
- MessageStoragePort -> ChannelEntity
  - ChannelProtocol.SyncUpdatedMessage 메시지 전달
    - ChannelEntity에 동기화된 수정된 채팅 메시지를 ChannelReaderActor로 전달하는 메시지

### 3. 수정된 채팅 메시지를 ChannelReaderActor에게 동기화

> 수정된 채팅 메시지를 Primary에 동기화한 이후 모든 Secondary에 동기화하는 과정

- ChannelEntity -> ChannelReaderActor
  - ChannelReaderProtocol.SyncUpdate 메시지 전달
    - ChannelEntity에 동기화된 수정된 채팅 메시지를 Secondary로 전달하는 메시지

### 4. 동기화한 수정된 채팅 메시지를 사용자 UI에 전달

> 수정된 채팅 메시지를 사용자 UI에 전달하는 과정

- ChannelReaderActor -> ClientSessionActor
  - ClientSessionProtocol.DeliverUpdatedMessage
    - ChannelEntity-ChannelReaderActor까지 완전히 동기화된, 수정된 채팅 메시지를 전달받는 메시지
- ClientSessionActor -> ClientMessageSender
  - ClientMessageSender.sendMessage() 메서드를 호출해 WebSocketSession에 수정된 메시지 전달

## 채팅 메시지 삭제

### 1. 채팅 메시지 삭제 요청

> 사용자가 채팅 메시지 삭제 요청

- 애플리케이션 서비스 -> ChannelEntity
  - ChannelProtocol.DeleteMessage 메시지 전달
    - 기존 채팅 메시지 삭제를 요청받는 메시지

### 2. 채팅 메시지 삭제 영속화

> 채팅 메시지 삭제 내용을 영속화하고, 그 결과를 ChannelEntity에게 전달하는 과정

- ChannelEntity -> MessageStoragePort
  - MessageStoragePort.delete()로 전달받은 메시지에 대한 영속화 진행
    - 메시지 전송 과정은 아니지만 수행 결과로 메시지를 전달하게 됨
- MessageStoragePort -> ChannelEntity
  - ChannelProtocol.SyncDeletedMessage 메시지 전달
    - 삭제된 채팅 메시지를 ChannelEntity에게 전달하는 메시지

### 3. 삭제된 채팅 메시지를 ChannelReaderActor에게 동기화

> 삭제된 채팅 메시지를 Primary에 동기화한 이후 모든 Secondary에 동기화하는 과정

- ChannelEntity -> ChannelReaderActor
  - ChannelReaderProtocol.SyncDeletion 메시지 전달
    - ChannelEntity에 동기화된 삭제된 채팅 메시지를 ChannelReaderActor로 전달하는 메시지

### 4. 동기화한 삭제된 채팅 메시지를 사용자 UI에 전달

> 삭제된 채팅 메시지를 사용자 UI에 전달하는 과정

- ChannelReaderActor -> ClientSessionActor
  - ClientSessionProtocol.DeliverDeletedMessage
    - ChannelEntity-ChannelReaderActor까지 완전히 동기화된, 삭제된 채팅 메시지를 전달받는 메시지
- ClientSessionActor -> ClientMessageSender
  - ClientMessageSender.sendMessage() 메서드를 호출해 WebSocketSession에 삭제된 메시지 전달

## ChannelEntity 채팅 메시지 동기화

### 1. ChannelEntity 초기화 시 채팅 메시지 동기화

> ChannelEntity 초기화 시 영속화된 채팅 메시지 중 최신 채팅 메시지 일부를 가져와 채팅 히스토리로 관리하기 위한 과정

- ChannelEntity -> MessageStoragePort
  - MessageStoragePort.findRecentMessages() 메서드 호출
    - 메시지 전송 과정은 아니지만 수행 결과로 ChannelEntity에게 영속화된 채팅 히스토리를 메시지로 전달
- MessageStoragePort -> ChannelEntity
  - ChannelProtocol.SyncRecentMessages 메시지 전달
    - 영속화된 채팅 메시지 중 최신 채팅 메시지 일부를 전달받는 메시지

## ChannelReaderActor ActorRef 조회 과정

> ChannelReaderRegistryActor에서 Singleton으로 관리하는 ChannelReaderActor를 조회하기 위한 과정

### 1. ChannelReaderActor ActorRef 요청

> WebSocketSession 연결 혹은 사용자가 다른 채널에 입장한 경우 이에 대한 ChannelReaderActor ActorRef를 관리하기 위한 과정

- ClientSessionActor -> ChannelReaderRegistryActor
  - ChannelReaderRegistryActor.GetChannelReaderActor 메시지 전달
    - ChannelReaderActor의 ActorRef 조회를 요청하는 메시지

### 2. 요청한 채널 ID에 대한 ChannelReaderActor ActorRef가 있는 경우

> ChannelReaderActor를 Singleton으로 관리하므로 해당 ChannelReaderActor가 생성되었는지 확인하고 있는 경우 수행되는 분기 로직
> 존재하는 ChannelReaderActor ActorRef를 바로 반환

- ChannelReaderRegistryActor -> ClientSessionActor
  - ClientSessionActor.FoundChannelReaders 메시지 전달
    - 요청한 채널 ID에 대해 ChannelReaderRegistryActor가 Singleton 방식으로 관리하는 ChannelReaderActor ActorRef를 전달받는 메시지

### 3. 요청한 채널 ID에 대한 ChannelReaderActor ActorRef가 없는 경우

> ChannelReaderActor를 Singleton으로 관리하므로 해당 ChannelReaderActor가 아직 생성되지 않았다면 새 Actor를 만들고 동기화 절차를 수행

- ChannelReaderRegistryActor에서 ChannelReaderActor를 자식으로 spawned
- ChannelReaderActor -> ChannelEntity
  - ChannelEntity.RequestSyncMessages 메시지 전달
    - ChannelReaderActor 생성 직후 ChannelEntity에게 최신 히스토리 스냅샷을 요청하는 메시지
- ChannelReaderActor -> ChannelReaderRegistryActor
  - ChannelReaderRegistryProtocol.SpawnedChannelReaderActor 메시지 전달
    - 자신이 생성되었음을 알리고 ChannelEntity에 등록할 readerName과 ActorRef를 전달하는 메시지
- ChannelReaderRegistryActor -> ChannelEntity
  - ChannelProtocol.RegisterReader 메시지 전달
    - ChannelEntity로부터 메시지를 동기화받는 ChannelReaderActor의 ActorRef를 전달하기 위한 메시지
- ChannelEntity -> ChannelReaderActor
  - ChannelReaderActor.DeliverSyncMessages 메시지 전달
    - Primary가 동기화한 채팅 히스토리를 Secondary가 전달받는 메시지
- ChannelReaderRegistryActor -> ClientSessionActor
  - ClientSessionActor.FoundChannelReaders 메시지 전달
    - 요청한 채널 ID에 대해 ChannelReaderRegistryActor가 Singleton 방식으로 관리하는 ChannelReaderActor ActorRef를 전달받는 메시지

## ChannelReaderRegistryActor HeartBeat

> JVM 레벨의 Singleton으로 관리되는 ChannelReaderActor를 사용하는 ClientSessionActor가 없을 때 이를 종료하기 위한 HeartBeat

### 1. ChannelReaderRegistryActor HeartBeat

> 내부 타이머로 메시지가 전달되는 과정

- ChannelReaderRegistryActor -> ChannelReaderRegistryActor
  - ChannelReaderRegistryActor.HeartBeat 메시지 전달
    - 내부 타이머를 활용해 240초 간격으로 전달되는 메시지

### 2. ChannelReaderActor가 유효한 경우

> ChannelReaderActor를 사용하고 있는 ClientSessionActor가 하나라도 있는 경우

- 아무 동작도 수행하지 않음

### 3. ChannelReaderActor가 유효하지 않은 경우

> ChannelReaderActor를 사용하고 있는 ClientSessionActor가 하나도 없는 경우

- ChannelReaderRegistryActor에서 사용되지 않는 ChannelReaderActor를 stop 처리
- ChannelReaderRegistryActor -> ChannelEntity
  - ChannelProtocol.RemoveShutdownReader 메시지 전달
    - ChannelEntity가 관리하고 있는 ChannelReaderActor 중 유효하지 않은 ChannelReaderActor의 제거 요청을 받는 메시지

## ChannelReaderActor가 Primary로부터 메시지 동기화를 요청하는 HeartBeat

> ChannelReaderActor에서 주기적으로 Primary부터 채팅 메시지 동기화를 요청하는 과정
> 채팅 메시지는 Primary로부터 일방적으로 전파받아야 하기 때문

### 1. ChannelReaderActor 타이머 트리거

> 30초 간격으로 트리거되는 과정

- ChannelReaderActor -> ChannelReaderActor
  - ChannelReaderActor.SyncMessageHeartBeat 메시지 전달
    - 30초 간격으로 ChannelEntity에 RequestSyncMessages를 보내도록 트리거하는 내부 타이머 메시지

### 2. ChannelEntity에게 채팅 메시지 동기화 요청

> ChannelEntity에게 채팅 메시지 동기화 요청 과정

- ChannelReaderActor -> ChannelEntity
  - ChannelEntity.RequestSyncMessages 메시지 전달
    - 채팅 히스토리 동기화를 요청하는 메시지

### 3. ChannelEntity가 ChannelReaderActor에게 동기화된 채팅 메시지 전달

> Primary가 Secondary에게 채팅 메시지를 동기화하는 과정

- ChannelEntity -> ChannelReaderActor
  - ChannelReaderActor.DeliverSyncMessages 메시지 전달
    - Primary가 동기화한 채팅 히스토리를 Secondary가 전달받는 메시지

## ClientSessionActor Shutdown

> ClientSessionActor Shutdown 과정

### 1. ClientSessionActor Shutdown 요청

> 외부에서 ClientSessionActor Shutdown을 요청받는 과정

- 외부 -> ClientSessionActor
  - ClientSessionProtocol.Shutdown 메시지 전달
    - ClientSessionActor 종료를 위한 메시지

### 2. 다른 Actor에게 ClientSessionActor 자신의 종료를 전파

> ChannelReaderRegistryActor, ChannelReaderActor에게 자신의 종료를 전파하는 과정

- ClientSessionActor -> ChannelReaderRegistryActor
  - ChannelReaderRegistryProtocol.ReleaseClientSessionActor 메시지 전달
    - ClientSessionActor가 종료되며 자신이 구독하던 channelId 목록을 Registry에 넘겨 ChannelReaderActor와의 매핑을 해제하도록 요청하는 메시지
- ClientSessionActor -> ChannelReaderActor
  - ChannelReaderProtocol.UnregisterClientSession
    - ClientSessionActor가 채널을 떠나거나 세션이 종료될 때 ChannelReaderActor가 해당 구독자를 해제하도록 요청하는 메시지

## 새로운 채널 참여

> 사용자가 새로운 채널에 입장한 상황을 Actor 구조에 전파해 필요한 정보를 모두 초기화하는 과정

### 1. 새로운 채널 참여 전파

> ClientSessionActor에 새로운 채널에 참여했음을 전파하는 과정

- 외부 -> ClientSessionActor
  - ClientSessionProtocol.SyncJoinChannel 메시지 전달
    - 외부에서 클라이언트가 새로운 채널에 참여했음을 전파하는 메시지

### 2. 새로운 채널의 Secondary인 ChannelReaderActor 조회

> 새로운 채널의 Secondary인 ChannelReaderActor 조회

- ClientSessionActor -> ChannelReaderRegistryActor
  - ChannelReaderRegistryActor.GetChannelReaderActor 메시지 전달
    - ChannelReaderActor의 ActorRef 조회를 요청하는 메시지
- ChannelReaderRegistryActor -> ClientSessionActor
  - ClientSessionActor.FoundChannelReaders 메시지 전달
    - 요청한 채널 ID에 대해 ChannelReaderRegistryActor가 Singleton 방식으로 관리하는 ChannelReaderActor ActorRef를 전달받는 메시지

## 채널 탈퇴

> 사용자가 기존에 참여하던 채널에 탈퇴하는 상황을 Actor 구조에 전파해 불필요한 리소스를 모두 정리하는 과정

### 1. 채널 탈퇴 전파

> ClientSessionActor에 채널을 탈퇴했음을 전파하는 과정

- 외부 -> ClientSessionActor
  - ClientSessionProtocol.SyncLeaveChannel 메시지 전달
    - 외부에서 클라이언트가 기존 채널에서 탈퇴했음을 전파하는 메시지

### 2. 채널 탈퇴 대상인 ChannelReaderActor에게 전파

> 채널 탈퇴 대상인 ChannelReaderActor에게 해당 ClientSessionActor가 더 이상 채널을 구독하지 않음을 전파하는 과정
> ChannelReaderActor는 전달받은 ClientSessionActor를 내부 구독자 목록에서 해제

- ClientSessionActor -> ChannelReaderActor
  - ChannelReaderProtocol.UnregisterClientSession
    - 특정 채널과의 연결을 해제하기 위해 ChannelReaderActor가 ClientSessionActor 구독을 해제하도록 요청하는 메시지

## ClientSessionActor WebSocketSession 단방향 Health Check

> ClientSessionActor와 WebSocketSession 간 Soft Failure Health Check를 수행하는 과정

### 1. ClientSessionActor 타이머 트리거

> 30초 간격으로 HeartBeat 메시지를 전달하는 과정

- ClientSessionActor -> ClientSessionActor
  - ClientSessionActor.SessionHealthCheck
    - 30초 간격으로 트리거되는 타이머로 인해 전달되는 메시지

### 2. WebSocketSession으로 Ping-Pong

> WebSocketSession으로 Ping-Pong Health Check를 수행하는 과정

- ClientSessionActor -> ClientMessageSender
  - ClientMessageSender.sendWebSocketPing() 메서드를 호출해 Ping 요청
- ClientMessageSender -> WebSocketSession
  - Sinks.Many<WebSocketPayload>에 Ping 전용 WebSocketPayload 전달
- WebSocketSession -> ClientSessionActor
  - ClientSessionProtocol.SessionPongReceived 메시지 전달
    - Client Session인 WebSocket Session으로부터 Pong을 전달하기 위한 메시지
