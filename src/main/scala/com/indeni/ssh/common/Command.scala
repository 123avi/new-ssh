package com.indeni.ssh.common

import com.indeni.ssh.common.IndTypes.ShellCommand


sealed trait Command

case object CloseChannel extends Command

case class SimpleCommand(cmd: ShellCommand) extends Command

// Map when receiving prompt which command to execute
case class ProcessPrompts[P <: Prompt](instructions: Map[P, ShellCommand]) extends Command

sealed trait Response

case object DoneProcessing extends Response

