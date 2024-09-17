// Copyright 2021-2024 FRC 6328
// http://github.com/Mechanical-Advantage
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// version 3 as published by the Free Software Foundation or
// available in the root directory of this project.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.

package frc.robot.subsystems.drive;

import static frc.robot.subsystems.drive.DriveConstants.*;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.controller.SimpleMotorFeedforward;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.system.plant.DCMotor;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.simulation.DCMotorSim;

/**
 * Physics sim implementation of module IO.
 *
 * <p>Uses two flywheel sims for the drive and turn motors, with the absolute position initialized
 * to a random value. The flywheel sims are not physically accurate, but provide a decent
 * approximation for the behavior of the module.
 */
public class ModuleIOSim implements ModuleIO {
  private static final double LOOP_PERIOD_SECS = 0.02;
  private final String name;

  private DCMotorSim driveSim = new DCMotorSim(DCMotor.getKrakenX60(1), 6.75, 0.025);
  private DCMotorSim turnSim = new DCMotorSim(DCMotor.getFalcon500(1), 150.0 / 7.0, 0.004);

  private final Rotation2d turnAbsoluteInitPosition = new Rotation2d(Math.random() * 2.0 * Math.PI);

  private double driveAppliedVolts = 0.0;
  private double turnAppliedVolts = 0.0;
  private PIDController turnController =
      new PIDController(turnSimFeedbackkP, turnSimFeedbackkI, turnSimFeedbackkD);
  private PIDController driveController =
      new PIDController(driveSimFeedBackkP, driveSimFeedBackkI, driveSimFeedBackkD);
  private SimpleMotorFeedforward driveFeedForward =
      new SimpleMotorFeedforward(driveSimFeedFowardkS, driveSimFeedFowardkV, driveSimFeedFowardkA);

  public ModuleIOSim(final String name) {
    turnController.enableContinuousInput(-Math.PI, Math.PI);
    this.name = name;
  }

  @Override
  public void updateInputs(final ModuleIOInputs inputs) {
    driveSim.update(LOOP_PERIOD_SECS);
    turnSim.update(LOOP_PERIOD_SECS);

    inputs.drivePositionMeters = driveSim.getAngularPositionRad() * Module.WHEEL_RADIUS;
    inputs.driveVelocityMetersPerSec = driveSim.getAngularVelocityRadPerSec() * Module.WHEEL_RADIUS;
    inputs.driveAppliedVolts = driveAppliedVolts;
    inputs.driveCurrentAmps = new double[] {Math.abs(driveSim.getCurrentDrawAmps())};

    inputs.turnAbsolutePosition =
        new Rotation2d(turnSim.getAngularPositionRad()).plus(turnAbsoluteInitPosition);
    inputs.turnPosition = new Rotation2d(turnSim.getAngularPositionRad());
    inputs.turnVelocityRadPerSec = turnSim.getAngularVelocityRadPerSec();
    inputs.turnAppliedVolts = turnAppliedVolts;
    inputs.turnCurrentAmps = new double[] {Math.abs(turnSim.getCurrentDrawAmps())};

    inputs.odometryTimestamps = new double[] {Timer.getFPGATimestamp()};
    inputs.odometryDrivePositionsMeters = new double[] {inputs.drivePositionMeters};
    inputs.odometryTurnPositions = new Rotation2d[] {inputs.turnPosition};
  }

  @Override
  public void setDriveVoltage(final double volts, final boolean focEnabled) {
    driveAppliedVolts = MathUtil.clamp(volts, -12.0, 12.0);
    driveSim.setInputVoltage(driveAppliedVolts);
  }

  @Override
  public void setTurnVoltage(final double volts) {
    turnAppliedVolts = MathUtil.clamp(volts, -12.0, 12.0);
    turnSim.setInputVoltage(turnAppliedVolts);
  }

  @Override
  public void setDriveSetpoint(final double metersPerSecond, final double metersPerSecondSquared) {
    setDriveVoltage(
        driveController.calculate(
                driveSim.getAngularVelocityRadPerSec() * Module.WHEEL_RADIUS, metersPerSecond)
            + driveFeedForward.calculate(metersPerSecond));
  }

  @Override
  public void setTurnSetpoint(final Rotation2d rotation) {
    setTurnVoltage(
        turnController.calculate(turnSim.getAngularPositionRotations(), rotation.getRotations()));
  }

  public String getModuleName() {
    return name;
  }
}
